from pydantic import BaseModel, Field, field_validator
from mas.models.coordinates import Coordinates


class VehicleAgent(BaseModel):
    """
    Autonomous Vehicle Agent base model representing an active fleet vehicle.
    Tracks vehicle identity, cargo priority for right-of-way contention, and current spatial location.
    """
    vehicle_id: str = Field(
        ...,
        min_length=1,
        description="Unique vehicle alphanumeric identifier (e.g., 'V-004')"
    )
    cargo_priority: int = Field(
        ...,
        ge=1,
        le=5,
        description="Cargo priority level: 1 (Standard Dry Goods) to 5 (Critical Medical Supplies)"
    )
    current_location: Coordinates = Field(
        ...,
        description="Current vehicle GPS position strictly in WGS 84 [longitude, latitude] within Klang Valley"
    )

    @field_validator("vehicle_id")
    @classmethod
    def validate_non_blank_id(cls, v: str) -> str:
        trimmed = v.strip()
        if not trimmed:
            raise ValueError("vehicle_id cannot be blank")
        return trimmed
