import pytest
from pydantic import ValidationError
from mas.models.coordinates import Coordinates
from mas.models.vehicle_agent import VehicleAgent


def test_vehicle_agent_valid_with_coordinates_object():
    loc = Coordinates(longitude=101.5810, latitude=3.0820)
    agent = VehicleAgent(
        vehicle_id="V-004",
        cargo_priority=5,
        current_location=loc
    )
    assert agent.vehicle_id == "V-004"
    assert agent.cargo_priority == 5
    assert agent.current_location.longitude == 101.5810
    assert agent.current_location.latitude == 3.0820
    assert agent.current_location.to_coords() == [101.5810, 3.0820]


def test_vehicle_agent_valid_with_array_coords():
    agent = VehicleAgent(
        vehicle_id="V-005",
        cargo_priority=1,
        current_location=[101.6500, 3.1100]  # Array coerced into Coordinates
    )
    assert agent.vehicle_id == "V-005"
    assert agent.cargo_priority == 1
    assert agent.current_location.to_coords() == [101.6500, 3.1100]


def test_vehicle_agent_cargo_priority_boundaries():
    # Priority 1 (minimum allowed)
    agent_p1 = VehicleAgent(vehicle_id="V-001", cargo_priority=1, current_location=[101.50, 3.00])
    assert agent_p1.cargo_priority == 1

    # Priority 5 (maximum allowed)
    agent_p5 = VehicleAgent(vehicle_id="V-002", cargo_priority=5, current_location=[101.50, 3.00])
    assert agent_p5.cargo_priority == 5

    # Priority 0 (< 1) must fail
    with pytest.raises(ValidationError) as exc_info:
        VehicleAgent(vehicle_id="V-003", cargo_priority=0, current_location=[101.50, 3.00])
    assert "cargo_priority" in str(exc_info.value)

    # Priority 6 (> 5) must fail
    with pytest.raises(ValidationError) as exc_info:
        VehicleAgent(vehicle_id="V-003", cargo_priority=6, current_location=[101.50, 3.00])
    assert "cargo_priority" in str(exc_info.value)


def test_vehicle_agent_empty_vehicle_id():
    with pytest.raises(ValidationError) as exc_info:
        VehicleAgent(vehicle_id="", cargo_priority=3, current_location=[101.50, 3.00])
    assert "vehicle_id" in str(exc_info.value)


def test_vehicle_agent_whitespace_vehicle_id():
    with pytest.raises(ValidationError) as exc_info:
        VehicleAgent(vehicle_id="   ", cargo_priority=3, current_location=[101.50, 3.00])
    assert "vehicle_id cannot be blank" in str(exc_info.value)


def test_vehicle_agent_invalid_location_propagation():
    with pytest.raises(ValidationError) as exc_info:
        VehicleAgent(
            vehicle_id="V-004",
            cargo_priority=4,
            current_location=[101.20, 3.00]  # Longitude out of Klang Valley
        )
    assert "longitude" in str(exc_info.value)
