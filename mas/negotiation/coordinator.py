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
        # In-flight / resolved conflict guard: set of (vehicle_id, anomaly_id)
        self._resolved_conflicts: set[tuple[str, str]] = set()
        self.decisions_log: list[RightOfWayDecision] = []

    def get_conflict_agents(self, anomaly_id: str) -> list[VehicleAgent]:
        """Returns the list of vehicle agents currently registered in conflict for an anomaly."""
        return list(self._anomaly_conflicts.get(anomaly_id, {}).values())

    def clear_conflict(self, anomaly_id: str) -> None:
        """Clears conflict tracking and resolved in-flight guards for an anomaly."""
        if anomaly_id in self._anomaly_conflicts:
            del self._anomaly_conflicts[anomaly_id]
        self._resolved_conflicts = {
            (vid, aid) for (vid, aid) in self._resolved_conflicts if aid != anomaly_id
        }
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
        conflict_key = (agent.vehicle_id, anomaly_id)

        # In-flight deduplication guard: skip duplicate poll cycles for an already-resolved agent
        if anomaly_id in self._anomaly_conflicts and agent.vehicle_id in self._anomaly_conflicts[anomaly_id]:
            if conflict_key in self._resolved_conflicts:
                logger.debug("Conflict %s already resolved or in-flight, skipping duplicate", conflict_key)
                return None

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
                anomaly=anomaly,
                penalty_factor=0.0,
            )
            return None

        # Case 2: Pairwise contention (FR 3.2: 2 conflicting vehicles)
        if len(conflicting_agents) == 2:
            decision = self.negotiator.negotiate_pair(
                agent_a=conflicting_agents[0],
                agent_b=conflicting_agents[1],
                anomaly_id=anomaly_id,
            )
            await self._apply_decision(decision, anomaly)
            return decision

        # Case 3: Multi-vehicle group contention (N > 2 vehicles)
        decisions = self.negotiator.negotiate_group(
            agents=conflicting_agents,
            anomaly_id=anomaly_id,
        )
        if decisions:
            # Apply decisions for the group
            for dec in decisions:
                await self._apply_decision(dec, anomaly)
            return decisions[0]

        return None

    async def _commit_detour(
        self,
        agent: VehicleAgent,
        corridor: DetourCorridor,
        rationale: str,
        anomaly: Anomaly | None = None,
        penalty_factor: float = 0.0,
        anomaly_id: str | None = None,
    ) -> dict[str, Any]:
        """
        Requests dynamic GraphHopper avoidance via AstraMcpClient.recalculate_detour()
        and commits the resulting [lng, lat] detour via update_route(), persisting the rationale.
        Coordinates are strictly validated in [longitude, latitude] order within Klang Valley bounds.
        """
        aid = anomaly.anomaly_id if anomaly else anomaly_id
        route_id = agent.route_id or f"RT-{agent.vehicle_id}"

        # Mark in-flight guard
        if aid:
            self._resolved_conflicts.add((agent.vehicle_id, aid))

        # Default to preset corridor coordinates
        coords = corridor.to_coordinates_array()
        distance_meters = corridor.distance_meters
        estimated_time_ms = int(corridor.estimated_time_seconds * 1000)

        # Dynamic recalculation attempt via GraphHopper if anomaly is available
        if anomaly:
            avoid_poly: list[list[float]] = []
            if anomaly.polygon_coordinates:
                avoid_poly = [[pt.longitude, pt.latitude] for pt in anomaly.polygon_coordinates]
            else:
                min_lng, min_lat, max_lng, max_lat = anomaly.bounding_box()
                avoid_poly = [
                    [min_lng, min_lat],
                    [max_lng, min_lat],
                    [max_lng, max_lat],
                    [min_lng, max_lat],
                    [min_lng, min_lat],
                ]

            origin_lng = agent.current_location.longitude
            origin_lat = agent.current_location.latitude
            dest_lng = corridor.waypoints[-1].longitude
            dest_lat = corridor.waypoints[-1].latitude
            if agent.upcoming_trajectory and len(agent.upcoming_trajectory) >= 2:
                dest_lng = agent.upcoming_trajectory[-1].longitude
                dest_lat = agent.upcoming_trajectory[-1].latitude

            if abs(origin_lng - dest_lng) > 0.0001 or abs(origin_lat - dest_lat) > 0.0001:
                try:
                    recalc_result = await self.mcp_client.recalculate_detour(
                        vehicle_id=agent.vehicle_id,
                        route_id=route_id,
                        origin_lng=origin_lng,
                        origin_lat=origin_lat,
                        dest_lng=dest_lng,
                        dest_lat=dest_lat,
                        avoid_polygon=avoid_poly,
                        penalty_factor=penalty_factor,
                        rationale=rationale,
                        anomaly_id=aid,
                    )
                    if isinstance(recalc_result, dict):
                        dyn_coords = recalc_result.get("geometry", {}).get("coordinates")
                        if dyn_coords and isinstance(dyn_coords, list) and len(dyn_coords) >= 2:
                            coords = dyn_coords
                            if "distanceMeters" in recalc_result and recalc_result["distanceMeters"] is not None:
                                distance_meters = float(recalc_result["distanceMeters"])
                            if "estimatedTimeMs" in recalc_result and recalc_result["estimatedTimeMs"] is not None:
                                estimated_time_ms = int(recalc_result["estimatedTimeMs"])
                except Exception as dyn_exc:
                    logger.debug("recalculate_detour fallback to corridor for %s: %s", agent.vehicle_id, dyn_exc)

        # Validate coordinate ordering and Klang Valley bounding box
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
            distance_meters=distance_meters,
            estimated_time_ms=estimated_time_ms,
            anomaly_id=aid,
        )

        agent.status = "REROUTING"
        agent.upcoming_trajectory = [Coordinates.model_validate(pt) for pt in coords]
        return result

    async def _apply_decision(
        self,
        decision: RightOfWayDecision,
        anomaly: Anomaly | str,
    ) -> None:
        """
        Applies a RightOfWayDecision by committing route updates for both winner and yielding vehicles.
        """
        anomaly_obj = anomaly if isinstance(anomaly, Anomaly) else None
        anomaly_id = anomaly.anomaly_id if isinstance(anomaly, Anomaly) else anomaly
        agents_map = self._anomaly_conflicts.get(anomaly_id, {})

        # 1. Update Winner Vehicle Route (Primary corridor, penalty 0.0)
        winner_agent = agents_map.get(decision.winner_vehicle_id)
        winner_corridor = decision.assigned_corridors.get(decision.winner_vehicle_id)
        if winner_agent and winner_corridor:
            await self._commit_detour(
                agent=winner_agent,
                corridor=winner_corridor,
                rationale=decision.rationale,
                anomaly=anomaly_obj,
                penalty_factor=0.0,
                anomaly_id=anomaly_id,
            )

        # 2. Update Yielding Vehicle Route (Secondary corridor, penalty 0.2)
        yielding_agent = agents_map.get(decision.yielding_vehicle_id)
        yielding_corridor = decision.assigned_corridors.get(decision.yielding_vehicle_id)
        if yielding_agent and yielding_corridor:
            await self._commit_detour(
                agent=yielding_agent,
                corridor=yielding_corridor,
                rationale=decision.rationale,
                anomaly=anomaly_obj,
                penalty_factor=0.2,
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
