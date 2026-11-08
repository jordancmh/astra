import pytest
from mas.server import mcp, ping, get_agent_status
from pydantic import ValidationError


def test_mcp_server_instance():
    assert mcp.name == "ASTRA MAS Engine"


def test_ping_tool():
    result = ping()
    assert isinstance(result, dict)
    assert result["status"] == "UP"
    assert result["service"] == "ASTRA MAS Engine"
    assert result["transport"] == "stdio"


def test_get_agent_status_tool_valid():
    result = get_agent_status(
        vehicle_id="V-004",
        cargo_priority=5,
        current_coords=[101.5810, 3.0820]
    )
    assert result["vehicle_id"] == "V-004"
    assert result["cargo_priority"] == 5
    assert result["current_coords"] == [101.5810, 3.0820]
    assert result["status"] == "READY"


def test_get_agent_status_tool_invalid_priority():
    with pytest.raises(ValidationError):
        get_agent_status(
            vehicle_id="V-004",
            cargo_priority=99,
            current_coords=[101.5810, 3.0820]
        )


def test_get_agent_status_tool_inverted_coords():
    # Inverted coordinates [lat, lng] must fail
    with pytest.raises(ValidationError):
        get_agent_status(
            vehicle_id="V-004",
            cargo_priority=5,
            current_coords=[3.0820, 101.5810]
        )
