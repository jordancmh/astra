"""
Unit tests for the Anomaly Pydantic schema (Issue #15, Task 4.1).
Validates bounds checking, [longitude, latitude] ordering, and JSON payload normalization.
"""
import pytest
from pydantic import ValidationError

from mas.models.anomaly import Anomaly, AnomalySeverity, AnomalyType
from mas.models.coordinates import Coordinates


def test_anomaly_valid_instantiation():
    """Verifies valid Anomaly creation with coordinates inside Klang Valley."""
    center = Coordinates(longitude=101.6210, latitude=3.1180)
    polygon = [
        Coordinates(longitude=101.6150, latitude=3.1120),
        Coordinates(longitude=101.6270, latitude=3.1120),
        Coordinates(longitude=101.6270, latitude=3.1240),
        Coordinates(longitude=101.6150, latitude=3.1240),
        Coordinates(longitude=101.6150, latitude=3.1120),
    ]
    anomaly = Anomaly(
        anomaly_id="D-001",
        anomaly_type=AnomalyType.ROAD_CLOSURE,
        severity=AnomalySeverity.HIGH,
        center=center,
        radius_meters=600.0,
        polygon_coordinates=polygon,
    )
    assert anomaly.anomaly_id == "D-001"
    assert anomaly.anomaly_type == AnomalyType.ROAD_CLOSURE
    assert anomaly.severity == AnomalySeverity.HIGH
    assert anomaly.center.to_coords() == [101.6210, 3.1180]
    assert anomaly.radius_meters == 600.0
    assert len(anomaly.polygon_coordinates) == 5
    assert anomaly.is_active is True


def test_anomaly_camel_case_aliases():
    """Verifies normalization of Spring Boot camelCase JSON and GeoJSON Polygon."""
    payload = {
        "anomalyId": "D-002",
        "anomalyType": "ACCIDENT",
        "severity": "CRITICAL",
        "center": [101.6500, 3.1200],
        "radiusMeters": 750.0,
        "polygon": {
            "type": "Polygon",
            "coordinates": [
                [
                    [101.6450, 3.1150],
                    [101.6550, 3.1150],
                    [101.6550, 3.1250],
                    [101.6450, 3.1250],
                    [101.6450, 3.1150],
                ]
            ],
        },
    }
    anomaly = Anomaly.model_validate(payload)
    assert anomaly.anomaly_id == "D-002"
    assert anomaly.anomaly_type == AnomalyType.ACCIDENT
    assert anomaly.severity == AnomalySeverity.CRITICAL
    assert anomaly.radius_meters == 750.0
    assert len(anomaly.polygon_coordinates) == 5
    assert anomaly.polygon_coordinates[0].to_coords() == [101.6450, 3.1150]


def test_anomaly_out_of_bounds_center():
    """Verifies that epicenter coordinates outside Klang Valley raise ValidationError."""
    # Longitude < 101.40
    with pytest.raises(ValidationError):
        Anomaly(
            anomaly_id="D-003",
            anomaly_type=AnomalyType.WEATHER,
            center=[101.2000, 3.1180],
        )

    # Latitude > 3.30
    with pytest.raises(ValidationError):
        Anomaly(
            anomaly_id="D-003",
            anomaly_type=AnomalyType.WEATHER,
            center=[101.6210, 3.4500],
        )


def test_anomaly_inverted_coordinates():
    """Verifies that inverted [latitude, longitude] coordinates fail validation."""
    with pytest.raises(ValidationError):
        Anomaly(
            anomaly_id="D-004",
            anomaly_type=AnomalyType.CONGESTION,
            center=[3.1180, 101.6210],  # Inverted!
        )


def test_anomaly_blank_id_rejected():
    """Verifies that blank or whitespace anomaly IDs are rejected."""
    with pytest.raises(ValidationError):
        Anomaly(
            anomaly_id="   ",
            anomaly_type=AnomalyType.ROAD_CLOSURE,
            center=[101.6210, 3.1180],
        )


def test_anomaly_bounding_box_computation():
    """Verifies AABB bounding box calculation for polygon and radius modes."""
    # Polygon mode
    anomaly_poly = Anomaly(
        anomaly_id="D-005",
        anomaly_type=AnomalyType.ROAD_CLOSURE,
        center=[101.6200, 3.1200],
        polygon_coordinates=[
            [101.6100, 3.1100],
            [101.6300, 3.1100],
            [101.6300, 3.1300],
            [101.6100, 3.1300],
        ],
    )
    min_lng, min_lat, max_lng, max_lat = anomaly_poly.bounding_box()
    assert min_lng == 101.6100
    assert max_lng == 101.6300
    assert min_lat == 3.1100
    assert max_lat == 3.1300

    # Radius-only mode
    anomaly_radius = Anomaly(
        anomaly_id="D-006",
        anomaly_type=AnomalyType.CONGESTION,
        center=[101.6000, 3.1000],
        radius_meters=555.0,  # ~0.005 degrees
        polygon_coordinates=[],
    )
    r_min_lng, r_min_lat, r_max_lng, r_max_lat = anomaly_radius.bounding_box()
    assert r_min_lng < 101.6000 < r_max_lng
    assert r_min_lat < 3.1000 < r_max_lat
