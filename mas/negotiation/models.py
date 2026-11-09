"""
ASTRA Multi-Agent System (MAS) - Negotiation Domain Models.
Defines schemas for detour corridors, peer-to-peer negotiation messages,
and right-of-way consensus decisions.
"""
from datetime import datetime, timezone
from enum import Enum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from mas.models.coordinates import Coordinates


class CorridorType(str, Enum):
    """Categorization of detour corridors for bottleneck mitigation (FR 3.2)."""
    PRIMARY = "PRIMARY"
    SECONDARY = "SECONDARY"
    TERTIARY = "TERTIARY"


class DetourCorridor(BaseModel):
    """
    Candidate detour corridor navigating around active disruption zones.
    Waypoints strictly enforce SRID 4326 [longitude, latitude] ordering within the Klang Valley.
    """
    model_config = ConfigDict(populate_by_name=True)

    corridor_id: str = Field(
        ...,
        alias="id",
        min_length=1,
        description="Unique corridor alphanumeric identifier (e.g., 'CORR-FED-HWY')",
    )
    name: str = Field(
        ...,
        min_length=1,
        description="Descriptive name of the corridor (e.g., 'Federal Highway Express')",
    )
    corridor_type: CorridorType = Field(
        default=CorridorType.PRIMARY,
        alias="corridorType",
        description="Priority tier of the corridor",
    )
    waypoints: list[Coordinates] = Field(
        ...,
        min_length=2,
        description="Ordered list of waypoint coordinates [longitude, latitude] within Klang Valley bounds",
    )
    distance_meters: float = Field(
        default=0.0,
        ge=0.0,
        alias="distanceMeters",
        description="Total corridor distance in meters",
    )
    estimated_time_seconds: float = Field(
        default=0.0,
        ge=0.0,
        alias="estimatedTimeSeconds",
        description="Estimated travel duration in seconds",
    )

    @field_validator("corridor_id")
    @classmethod
    def validate_non_blank_id(cls, v: str) -> str:
        trimmed = v.strip()
        if not trimmed:
            raise ValueError("corridor_id cannot be blank")
        return trimmed

    def to_coordinates_array(self) -> list[list[float]]:
        """Returns standard GeoJSON coordinate array [[lng, lat], ...] for MCP updateRoute tool calls."""
        return [c.to_coords() for c in self.waypoints]


class NegotiationMessage(BaseModel):
    """
    Message payload exchanged between autonomous vehicle agent nodes during right-of-way negotiation.
    """
    model_config = ConfigDict(populate_by_name=True)

    sender_id: str = Field(
        ...,
        alias="senderId",
        min_length=1,
        description="Vehicle identifier of sending agent (e.g. 'V-004')",
    )
    recipient_id: str | None = Field(
        default=None,
        alias="recipientId",
        description="Target vehicle identifier, or None / 'ALL' for broadcast",
    )
    anomaly_id: str = Field(
        ...,
        alias="anomalyId",
        min_length=1,
        description="Identifier of disruption prompting negotiation (e.g. 'D-001')",
    )
    cargo_priority: int = Field(
        ...,
        ge=1,
        le=5,
        alias="cargoPriority",
        description="Cargo priority (1=Dry Goods to 5=Critical Medical)",
    )
    speed_kmh: float = Field(
        default=0.0,
        ge=0.0,
        alias="speedKmh",
        description="Current speed in km/h for momentum tie-breaking",
    )
    current_coords: Coordinates = Field(
        ...,
        alias="currentCoords",
        description="Current vehicle GPS position strictly in WGS 84 [longitude, latitude]",
    )
    proposed_corridor_id: str | None = Field(
        default=None,
        alias="proposedCorridorId",
        description="Identifier of preferred detour corridor",
    )
    rationale: str | None = Field(
        default=None,
        description="Optional justification text generated during negotiation",
    )

    @field_validator("sender_id")
    @classmethod
    def validate_non_blank_sender(cls, v: str) -> str:
        trimmed = v.strip()
        if not trimmed:
            raise ValueError("sender_id cannot be blank")
        return trimmed


class RightOfWayDecision(BaseModel):
    """
    Consensus outcome of a right-of-way negotiation between two or more conflicting agents (FR 3.2).
    """
    model_config = ConfigDict(populate_by_name=True)

    anomaly_id: str = Field(
        ...,
        alias="anomalyId",
        min_length=1,
        description="Disruption identifier that triggered negotiation",
    )
    winner_vehicle_id: str = Field(
        ...,
        alias="winnerVehicleId",
        min_length=1,
        description="Vehicle granted primary right-of-way",
    )
    yielding_vehicle_id: str = Field(
        ...,
        alias="yieldingVehicleId",
        min_length=1,
        description="Vehicle yielding to secondary detour corridor",
    )
    assigned_corridors: dict[str, DetourCorridor] = Field(
        default_factory=dict,
        alias="assignedCorridors",
        description="Mapping of vehicle_id to allocated DetourCorridor",
    )
    rationale: str = Field(
        ...,
        description="Human-readable negotiation log entry formatted for NEGOTIATION_EVENT table",
    )
    tie_breaker_reason: str = Field(
        default="cargo_priority",
        alias="tieBreakerReason",
        description="Factor determining right-of-way ('cargo_priority', 'speed_kmh', 'vehicle_id')",
    )
    resolved_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        alias="resolvedAt",
        description="UTC timestamp when negotiation consensus was reached",
    )
