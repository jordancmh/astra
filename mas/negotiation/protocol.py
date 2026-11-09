"""
ASTRA Multi-Agent System (MAS) - Right-of-Way Negotiation Protocol Engine.
Implements the 3-tier deterministic negotiation rules:
1. cargo_priority (1 to 5) (FR 3.2)
2. speed_kmh (momentum preservation tie-breaker)
3. vehicle_id (alphabetical consensus tie-breaker)
"""
import logging
from collections.abc import Sequence

from mas.models.coordinates import Coordinates
from mas.models.vehicle_agent import VehicleAgent
from mas.negotiation.models import (
    CorridorType,
    DetourCorridor,
    RightOfWayDecision,
)

logger = logging.getLogger(__name__)

# Default standard Klang Valley detour corridors (SRID 4326, [longitude, latitude])
DEFAULT_PRIMARY_CORRIDOR = DetourCorridor(
    corridor_id="CORR-FED-HWY-01",
    name="Federal Highway Express",
    corridor_type=CorridorType.PRIMARY,
    waypoints=[
        Coordinates(longitude=101.5810, latitude=3.0820),
        Coordinates(longitude=101.6500, latitude=3.1100),
        Coordinates(longitude=101.7116, latitude=3.1579),
    ],
    distance_meters=18_500.0,
    estimated_time_seconds=1_200.0,
)

DEFAULT_SECONDARY_CORRIDOR = DetourCorridor(
    corridor_id="CORR-NPE-02",
    name="New Pantai Expressway (NPE)",
    corridor_type=CorridorType.SECONDARY,
    waypoints=[
        Coordinates(longitude=101.5830, latitude=3.0815),
        Coordinates(longitude=101.6300, latitude=3.0750),
        Coordinates(longitude=101.6980, latitude=3.1480),
    ],
    distance_meters=21_200.0,
    estimated_time_seconds=1_450.0,
)


class RightOfWayNegotiator:
    """
    Evaluates conflicting vehicle agents contending for routes around an anomaly (FR 3.2).
    """

    def __init__(
        self,
        default_primary_corridor: DetourCorridor | None = None,
        default_secondary_corridor: DetourCorridor | None = None,
    ) -> None:
        self.primary_corridor = default_primary_corridor or DEFAULT_PRIMARY_CORRIDOR
        self.secondary_corridor = default_secondary_corridor or DEFAULT_SECONDARY_CORRIDOR

    def compare_agents(
        self,
        agent_a: VehicleAgent,
        agent_b: VehicleAgent,
    ) -> tuple[VehicleAgent, VehicleAgent, str]:
        """
        Determines right-of-way between two agents using deterministic 3-tier rules:
        1. Higher cargo_priority (1 to 5) wins.
        2. Higher speed_kmh wins (momentum preservation).
        3. Alphabetically lower vehicle_id wins (consensus tie-breaker).

        Returns:
            (winner, yielding, tie_breaker_reason)
        """
        # Tier 1: Cargo Priority (FR 3.2)
        if agent_a.cargo_priority != agent_b.cargo_priority:
            if agent_a.cargo_priority > agent_b.cargo_priority:
                return agent_a, agent_b, "cargo_priority"
            return agent_b, agent_a, "cargo_priority"

        # Tier 2: Speed / Momentum Preservation
        if agent_a.speed_kmh != agent_b.speed_kmh:
            if agent_a.speed_kmh > agent_b.speed_kmh:
                return agent_a, agent_b, "speed_kmh"
            return agent_b, agent_a, "speed_kmh"

        # Tier 3: Deterministic Vehicle ID Ordering
        if agent_a.vehicle_id < agent_b.vehicle_id:
            return agent_a, agent_b, "vehicle_id"
        return agent_b, agent_a, "vehicle_id"

    def format_rationale(
        self,
        winner: VehicleAgent,
        yielding: VehicleAgent,
        anomaly_id: str,
        tie_breaker_reason: str,
        primary_corridor: DetourCorridor,
        secondary_corridor: DetourCorridor,
    ) -> str:
        """
        Formats a standardized negotiation summary suitable for the backend NEGOTIATION_EVENT log.
        """
        reason_desc = {
            "cargo_priority": f"Priority {winner.cargo_priority} vs {yielding.cargo_priority}",
            "speed_kmh": f"Higher momentum ({winner.speed_kmh:.1f} km/h vs {yielding.speed_kmh:.1f} km/h)",
            "vehicle_id": f"Consensus identifier precedence ({winner.vehicle_id} < {yielding.vehicle_id})",
        }.get(tie_breaker_reason, tie_breaker_reason)

        return (
            f"Conflict at {anomaly_id} resolved: {winner.vehicle_id} granted primary detour via "
            f"{primary_corridor.name}; {yielding.vehicle_id} yielded to {secondary_corridor.name} "
            f"corridor ({reason_desc})."
        )

    def negotiate_pair(
        self,
        agent_a: VehicleAgent,
        agent_b: VehicleAgent,
        anomaly_id: str,
        primary_corridor: DetourCorridor | None = None,
        secondary_corridor: DetourCorridor | None = None,
    ) -> RightOfWayDecision:
        """
        Performs pairwise negotiation, assigns corridors, and generates consensus decision.
        """
        p_corridor = primary_corridor or self.primary_corridor
        s_corridor = secondary_corridor or self.secondary_corridor

        winner, yielding, reason = self.compare_agents(agent_a, agent_b)
        rationale = self.format_rationale(
            winner=winner,
            yielding=yielding,
            anomaly_id=anomaly_id,
            tie_breaker_reason=reason,
            primary_corridor=p_corridor,
            secondary_corridor=s_corridor,
        )

        decision = RightOfWayDecision(
            anomaly_id=anomaly_id,
            winner_vehicle_id=winner.vehicle_id,
            yielding_vehicle_id=yielding.vehicle_id,
            assigned_corridors={
                winner.vehicle_id: p_corridor,
                yielding.vehicle_id: s_corridor,
            },
            rationale=rationale,
            tie_breaker_reason=reason,
        )
        logger.info(
            "Negotiated consensus for %s: %s (Winner) -> %s, %s (Yielding) -> %s [Factor: %s]",
            anomaly_id,
            winner.vehicle_id,
            p_corridor.name,
            yielding.vehicle_id,
            s_corridor.name,
            reason,
        )
        return decision

    def negotiate_group(
        self,
        agents: Sequence[VehicleAgent],
        anomaly_id: str,
        corridors: Sequence[DetourCorridor] | None = None,
    ) -> list[RightOfWayDecision]:
        """
        Resolves multi-vehicle corridor contention by ranking agents and allocating available corridors.
        """
        if len(agents) < 2:
            return []

        # Sort agents by (-cargo_priority, -speed_kmh, vehicle_id)
        ranked = sorted(
            agents,
            key=lambda a: (-a.cargo_priority, -a.speed_kmh, a.vehicle_id),
        )

        available = list(corridors) if corridors else [self.primary_corridor, self.secondary_corridor]

        decisions: list[RightOfWayDecision] = []
        for i in range(len(ranked) - 1):
            winner = ranked[i]
            yielding = ranked[i + 1]

            p_corr = available[min(i, len(available) - 1)]
            s_corr = available[min(i + 1, len(available) - 1)]

            _, _, reason = self.compare_agents(winner, yielding)
            rationale = self.format_rationale(
                winner=winner,
                yielding=yielding,
                anomaly_id=anomaly_id,
                tie_breaker_reason=reason,
                primary_corridor=p_corr,
                secondary_corridor=s_corr,
            )

            decision = RightOfWayDecision(
                anomaly_id=anomaly_id,
                winner_vehicle_id=winner.vehicle_id,
                yielding_vehicle_id=yielding.vehicle_id,
                assigned_corridors={
                    winner.vehicle_id: p_corr,
                    yielding.vehicle_id: s_corr,
                },
                rationale=rationale,
                tie_breaker_reason=reason,
            )
            decisions.append(decision)

        return decisions
