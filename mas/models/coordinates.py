from typing import Any
from pydantic import BaseModel, Field, model_validator


class Coordinates(BaseModel):
    """
    Spatial coordinates strictly bounded to the Klang Valley region (SRID 4326).
    Enforces strict [longitude, latitude] ordering.
    """
    longitude: float = Field(
        ...,
        ge=101.40,
        le=101.90,
        description="Longitude in WGS 84 (Klang Valley bounds: 101.40 to 101.90)"
    )
    latitude: float = Field(
        ...,
        ge=2.90,
        le=3.30,
        description="Latitude in WGS 84 (Klang Valley bounds: 2.90 to 3.30)"
    )

    @model_validator(mode="before")
    @classmethod
    def parse_coordinate_array(cls, data: Any) -> Any:
        if isinstance(data, (list, tuple)):
            if len(data) != 2:
                raise ValueError("Coordinates list or tuple must contain exactly 2 elements: [longitude, latitude]")
            return {"longitude": data[0], "latitude": data[1]}
        return data

    def to_coords(self) -> list[float]:
        """Returns standard GeoJSON [longitude, latitude] coordinate array."""
        return [self.longitude, self.latitude]
