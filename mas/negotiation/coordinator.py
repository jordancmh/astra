"""
ASTRA Multi-Agent System (MAS) - Negotiation Coordinator.
Integrates FleetMonitor collision events with the RightOfWayNegotiator
and commits negotiated detour corridors back to the Spring Boot MCP broker (FR 3.2, FR 3.3).
"""
import logging
from typing import Any

from mas.detection.spatial import haversine_distance
from mas.mcp_client import AstraMcpClient
from mas.models.anomaly import Anomaly
from mas.models.coordinates import Coordinates
from mas.models.vehicle_agent import VehicleAgent
from mas.negotiation.models import DetourCorridor, RightOfWayDecision
from mas.negotiation.protocol import RightOfWayNegotiator

logger = logging.getLogger(__name__)


class NegotiationCoordinator:
    """
    Coordinates right-of-way resolution between contending vehicle agents
    and persists negotiated detours via AstraMcpClient (FR 3.2, FR 3.3, NFR 2.1).
    """

    def __init__(
        self,
        mcp_client: AstraMcpClient,
        negotiator: RightOfWayNegotiator | None = None,
    ) -> None:
        self.mcp_client = mcp_client
        self.negotiator = negotiator or RightOfWayNegotiator()
        # Maps anomaly_id -> {vehicle_id: agent}
        self._anomaly_conflicts: dict[str, dict[str, VehicleAgent]] = {}
        self.decisions_log: list[RightOfWayDecision] = []

    def get_conflict_agents(self, anomaly_id: str) -> list[VehicleAgent]:
        """Returns the list of vehicle agents currently registered in conflict for an anomaly."""
        return list(self._anomaly_conflicts.get(anomaly_id, {}).values())

    def clear_conflict(self, anomaly_id: str) -> None:
        """Clears conflict tracking for an anomaly."""
        if anomaly_id in self._anomaly_conflicts:
            del self._anomaly_conflicts[anomaly_id]
            logger.info("Cleared conflict tracking for anomaly %s", anomaly_id)

    async def handle_collision(
        self,
        agent: VehicleAgent,
        anomaly: Anomaly,
    ) -> RightOfWayDecision | None:
        """
        Async event handler designed to hook directly into FleetMonitor.on_collision.
        When multiple vehicles intersect the same disruption, triggers peer negotiation
        and commits detour geometries via MCP.
        """
        anomaly_id = anomaly.anomaly_id
        if anomaly_id not in self._anomaly_conflicts:
            self._anomaly_conflicts[anomaly_id] = {}

        self._anomaly_conflicts[anomaly_id][agent.vehicle_id] = agent
        conflicting_agents = list(self._anomaly_conflicts[anomaly_id].values())

        logger.info(
            "Conflict registered at %s: Vehicle %s (Total conflicting: %d)",
            anomaly_id,
            agent.vehicle_id,
            len(conflicting_agents),
        )

        # Case 1: Single vehicle disruption (no contention with other agents)
        if len(conflicting_agents) == 1:
            corridor = self.negotiator.primary_corridor
            await self._commit_detour(
                agent=agent,
                corridor=corridor,
                rationale=(
                    f"Single vehicle disruption at {anomaly_id}: {agent.vehicle_id} assigned "
                    f"primary corridor {corridor.name}."
                ),
                anomaly_id=anomaly_id,
            )
            return None

        # Case 2: Pairwise contention (FR 3.2: 2 conflicting vehicles)
        if len(conflicting_agents) == 2:
            decision = self.negotiator.negotiate_pair(
                agent_a=conflicting_agents[0],
                agent_b=conflicting_agents[1],
                anomaly_id=anomaly_id,
            )
            await self._apply_decision(decision, anomaly_id)
            return decision

        # Case 3: Multi-vehicle group contention (N > 2 vehicles)
        decisions = self.negotiator.negotiate_group(
            agents=conflicting_agents,
            anomaly_id=anomaly_id,
        )
        if decisions:
            # Apply decisions for the group
            for dec in decisions:
                await self._apply_decision(dec, anomaly_id)
            return decisions[0]

        return None

    async def _commit_detour(
        self,
        agent: VehicleAgent,
        corridor: DetourCorridor,
        rationale: str,
        anomaly_id: str | None = None,
    ) -> dict[str, Any]:
        """
        Commits a detour corridor LineString via AstraMcpClient.update_route.
        Coordinates are strictly validated in [longitude, latitude] order within Klang Valley bounds.
        """
        route_id = agent.route_id or f"RT-{agent.vehicle_id}"
        coords = corridor.to_coordinates_array()

        # Guardrail: Pre-validate coordinate ordering and bounds
        for pt in coords:
            Coordinates.model_validate(pt)

        logger.info(
            "Submitting updateRoute for vehicle %s (Route %s) via corridor %s (%d points)",
            agent.vehicle_id,
            route_id,
            corridor.name,
            len(coords),
        )

        result = await self.mcp_client.update_route(
            vehicle_id=agent.vehicle_id,
            route_id=route_id,
            coordinates=coords,
            rationale=rationale,
            anomaly_id=anomaly_id,
        )

        # Update local agent status to REROUTING and assign new trajectory
        agent.status = "REROUTING"
        agent.upcoming_trajectory = list(corridor.waypoints)
        return result

    async def _apply_decision(
        self,
        decision: RightOfWayDecision,
        anomaly_id: str,
    ) -> None:
        """
        Applies a RightOfWayDecision by committing route updates for both winner and yielding vehicles.
        """
        agents_map = self._anomaly_conflicts.get(anomaly_id, {})

        # 1. Update Winner Vehicle Route
        winner_agent = agents_map.get(decision.winner_vehicle_id)
        winner_corridor = decision.assigned_corridors.get(decision.winner_vehicle_id)
        if winner_agent and winner_corridor:
            await self._commit_detour(
                agent=winner_agent,
                corridor=winner_corridor,
                rationale=decision.rationale,
                anomaly_id=anomaly_id,
            )

        # 2. Update Yielding Vehicle Route
        yielding_agent = agents_map.get(decision.yielding_vehicle_id)
        yielding_corridor = decision.assigned_corridors.get(decision.yielding_vehicle_id)
        if yielding_agent and yielding_corridor:
            await self._commit_detour(
                agent=yielding_agent,
                corridor=yielding_corridor,
                rationale=decision.rationale,
                anomaly_id=anomaly_id,
            )

        self.decisions_log.append(decision)
        logger.info(
            "Committed RightOfWayDecision for %s: %s (Primary) and %s (Secondary)",
            anomaly_id,
            decision.winner_vehicle_id,
            decision.yielding_vehicle_id,
        )

    def get_decisions(self) -> list[RightOfWayDecision]:
        """Returns the chronological history of all resolved right-of-way decisions."""
        return list(self.decisions_log)
