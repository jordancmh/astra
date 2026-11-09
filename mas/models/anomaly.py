"""
ASTRA Multi-Agent System (MAS) - Anomaly Domain Model.
Represents active network disruptions within the Klang Valley region (SRID 4326).
"""
import math
from enum import Enum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from mas.models.coordinates import Coordinates


class AnomalyType(str, Enum):
    """Types of synthetic disruptions defined in ASTRA functional specs (FR 1.3)."""
    ROAD_CLOSURE = "ROAD_CLOSURE"
    CONGESTION = "CONGESTION"
    WEATHER = "WEATHER"
    ACCIDENT = "ACCIDENT"


class AnomalySeverity(str, Enum):
    """Severity ratings for disruptions."""
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class Anomaly(BaseModel):
    """
    Pydantic schema representing an active road network anomaly/disruption.
    Bounded strictly to the Klang Valley [longitude, latitude] coordinates (SRID 4326).
    Supports camelCase and snake_case field aliases for JSON interoperability.
    """
    model_config = ConfigDict(populate_by_name=True)

    anomaly_id: str = Field(
        ...,
        alias="id",
        min_length=1,
        description="Unique identifier for disruption (e.g., 'D-001')",
    )
    anomaly_type: AnomalyType = Field(
        ...,
        alias="type",
        description="Disruption category (ROAD_CLOSURE, CONGESTION, WEATHER, ACCIDENT)",
    )
    severity: AnomalySeverity = Field(
        default=AnomalySeverity.HIGH,
        description="Severity level of the disruption",
    )
    center: Coordinates = Field(
        ...,
        description="Epicenter of disruption in WGS 84 [longitude, latitude]",
    )
    radius_meters: float = Field(
        default=500.0,
        ge=0.0,
        alias="radiusMeters",
        description="Impact radius in meters around the epicenter",
    )
    polygon_coordinates: list[Coordinates] = Field(
        default_factory=list,
        alias="polygonCoordinates",
        description="Ordered list of coordinates defining the disruption boundary polygon",
    )
    is_active: bool = Field(
        default=True,
        alias="isActive",
        description="Flag indicating if the disruption is currently active",
    )

    @field_validator("anomaly_id")
    @classmethod
    def validate_non_blank_id(cls, v: str) -> str:
        trimmed = v.strip()
        if not trimmed:
            raise ValueError("anomaly_id cannot be blank")
        return trimmed

    @model_validator(mode="before")
    @classmethod
    def parse_payload_aliases(cls, data: Any) -> Any:
        """
        Normalizes backend JSON payloads:
        - Resolves 'anomalyId' -> 'id'
        - Resolves 'anomalyType' -> 'type'
        - Resolves GeoJSON 'polygon' structure -> 'polygon_coordinates'
        """
        if not isinstance(data, dict):
            return data

        d = dict(data)

        # Map alternate ID keys
        if "anomalyId" in d and "id" not in d and "anomaly_id" not in d:
            d["id"] = d["anomalyId"]

        # Map alternate Type keys
        if "anomalyType" in d and "type" not in d and "anomaly_type" not in d:
            d["type"] = d["anomalyType"]

        # Extract polygon coordinates from GeoJSON or raw lists
        if "polygon" in d and ("polygon_coordinates" not in d and "polygonCoordinates" not in d):
            poly = d["polygon"]
            if isinstance(poly, dict) and "coordinates" in poly:
                coords = poly["coordinates"]
                # GeoJSON Polygon coordinates are [[[lng, lat], ...]]
                if coords and isinstance(coords[0], list) and coords[0] and isinstance(coords[0][0], list):
                    d["polygon_coordinates"] = coords[0]
                elif coords and isinstance(coords[0], list):
                    d["polygon_coordinates"] = coords
            elif isinstance(poly, list):
                d["polygon_coordinates"] = poly

        return d

    def bounding_box(self) -> tuple[float, float, float, float]:
        """
        Returns the Axis-Aligned Bounding Box (AABB) as (min_lng, min_lat, max_lng, max_lat).
        If polygon_coordinates are provided, uses their envelope.
        Otherwise, derives bounding box from center and radius_meters.
        """
        if self.polygon_coordinates:
            min_lng = min(c.longitude for c in self.polygon_coordinates)
            max_lng = max(c.longitude for c in self.polygon_coordinates)
            min_lat = min(c.latitude for c in self.polygon_coordinates)
            max_lat = max(c.latitude for c in self.polygon_coordinates)
            return (min_lng, min_lat, max_lng, max_lat)

        # Approximate bounding box from center point and radius_meters
        lat_rad = math.radians(self.center.latitude)
        meters_per_deg_lat = 111_000.0
        meters_per_deg_lng = 111_000.0 * max(0.1, math.cos(lat_rad))

        lat_delta = self.radius_meters / meters_per_deg_lat
        lng_delta = self.radius_meters / meters_per_deg_lng

        return (
            self.center.longitude - lng_delta,
            self.center.latitude - lat_delta,
            self.center.longitude + lng_delta,
            self.center.latitude + lat_delta,
        )
