from typing import Any
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from mas.models.coordinates import Coordinates


class VehicleAgent(BaseModel):
    """
    Autonomous Vehicle Agent base model representing an active fleet vehicle.
    Tracks vehicle identity, cargo priority for right-of-way contention, current spatial location,
    operational status, and upcoming route trajectory.
    """
    model_config = ConfigDict(populate_by_name=True)

    vehicle_id: str = Field(
        ...,
        alias="id",
        min_length=1,
        description="Unique vehicle alphanumeric identifier (e.g., 'V-004')",
    )
    cargo_priority: int = Field(
        default=1,
        ge=1,
        le=5,
        alias="cargoPriority",
        description="Cargo priority level: 1 (Standard Dry Goods) to 5 (Critical Medical Supplies)",
    )
    current_location: Coordinates = Field(
        ...,
        alias="currentLocation",
        description="Current vehicle GPS position strictly in WGS 84 [longitude, latitude] within Klang Valley",
    )
    status: str = Field(
        default="NOMINAL",
        description="Operational status: 'NOMINAL', 'INTERSECTED', 'DISRUPTED', 'REROUTING', 'IDLE'",
    )
    route_id: str | None = Field(
        default=None,
        alias="routeId",
        description="Associated active route identifier (e.g. 'RT-001')",
    )
    speed_kmh: float = Field(
        default=0.0,
        ge=0.0,
        alias="speedKmh",
        description="Current vehicle velocity in km/h",
    )
    bearing: float = Field(
        default=0.0,
        ge=0.0,
        le=360.0,
        description="Vehicle orientation in degrees (0 to 360)",
    )
    upcoming_trajectory: list[Coordinates] = Field(
        default_factory=list,
        alias="upcomingTrajectory",
        description="Ordered list of upcoming waypoint coordinates [longitude, latitude]",
    )
    intersected_anomaly_id: str | None = Field(
        default=None,
        alias="intersectedAnomalyId",
        description="Identifier of the anomaly causing an INTERSECTED status",
    )

    @field_validator("vehicle_id")
    @classmethod
    def validate_non_blank_id(cls, v: str) -> str:
        trimmed = v.strip()
        if not trimmed:
            raise ValueError("vehicle_id cannot be blank")
        return trimmed

    @model_validator(mode="before")
    @classmethod
    def parse_payload_aliases(cls, data: Any) -> Any:
        """
        Normalizes backend JSON payloads:
        - Resolves 'vehicleId' -> 'id'
        - Resolves 'currentCoords' or 'coordinates' -> 'currentLocation'
        - Resolves 'trajectory' -> 'upcomingTrajectory'
        """
        if not isinstance(data, dict):
            return data

        d = dict(data)

        # Map alternate ID keys
        if "vehicleId" in d and "id" not in d and "vehicle_id" not in d:
            d["id"] = d["vehicleId"]

        # Map alternate location keys
        if "currentCoords" in d and "currentLocation" not in d and "current_location" not in d:
            d["currentLocation"] = d["currentCoords"]
        elif "current_coords" in d and "currentLocation" not in d and "current_location" not in d:
            d["currentLocation"] = d["current_coords"]

        # Map alternate trajectory keys
        if "trajectory" in d and "upcomingTrajectory" not in d and "upcoming_trajectory" not in d:
            d["upcomingTrajectory"] = d["trajectory"]

        return d

    def set_intersected(self, anomaly_id: str) -> None:
        """Flags vehicle agent with INTERSECTED status and assigns the anomaly ID."""
        self.status = "INTERSECTED"
        self.intersected_anomaly_id = anomaly_id

    def clear_intersection(self) -> None:
        """Clears intersection state back to NOMINAL."""
        self.status = "NOMINAL"
        self.intersected_anomaly_id = None
