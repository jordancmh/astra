"""
Unit tests for MCP synchronization to PostGIS database (Issue #17).
Verifies AstraMcpClient.update_route with extended metrics and recalculate_detour.
"""
from unittest.mock import AsyncMock

import pytest

from mas.mcp_client import AstraMcpClient
from mas.models.anomaly import Anomaly
from mas.models.vehicle_agent import VehicleAgent
from mas.negotiation.coordinator import NegotiationCoordinator
from mas.negotiation.models import DetourCorridor


@pytest.mark.asyncio
async def test_update_route_sends_anomaly_id_and_metrics() -> None:
    client = AstraMcpClient()
    client._session = AsyncMock()

    mock_result = AsyncMock()
    mock_result.is_error = False
    mock_result.content = [
        AsyncMock(text='{"status":"COMMITTED","vehicleId":"V-001","routeId":"RT-001","distanceMeters":35000.0,"estimatedTimeMs":2100000}')
    ]
    client._session.call_tool.return_value = mock_result

    coords = [[101.5810, 3.0820], [101.7116, 3.1579]]
    response = await client.update_route(
        vehicle_id="V-001",
        route_id="RT-001",
        coordinates=coords,
        rationale="Detour via Federal Hwy",
        distance_meters=35000.0,
        estimated_time_ms=2100000,
        anomaly_id="D-001",
    )

    assert response["status"] == "COMMITTED"
    assert response["vehicleId"] == "V-001"
    assert response["distanceMeters"] == 35000.0

    client._session.call_tool.assert_awaited_once_with(
        "updateRoute",
        arguments={
            "vehicleId": "V-001",
            "routeId": "RT-001",
            "coordinates": coords,
            "rationale": "Detour via Federal Hwy",
            "distanceMeters": 35000.0,
            "estimatedTimeMs": 2100000,
            "anomalyId": "D-001",
        },
    )


@pytest.mark.asyncio
async def test_recalculate_detour_calls_mcp_tool() -> None:
    client = AstraMcpClient()
    client._session = AsyncMock()

    mock_result = AsyncMock()
    mock_result.is_error = False
    mock_result.content = [
        AsyncMock(text='{"status":"COMMITTED","vehicleId":"V-004","routeId":"RT-004","distanceMeters":42000.0}')
    ]
    client._session.call_tool.return_value = mock_result

    avoid_poly = [
        [101.615, 3.112],
        [101.627, 3.112],
        [101.627, 3.124],
        [101.615, 3.124],
        [101.615, 3.112],
    ]

    response = await client.recalculate_detour(
        vehicle_id="V-004",
        route_id="RT-004",
        origin_lng=101.4496,
        origin_lat=3.0430,
        dest_lng=101.7116,
        dest_lat=3.1579,
        avoid_polygon=avoid_poly,
        penalty_factor=0.0,
        rationale="Recalculated avoiding D-001",
        anomaly_id="D-001",
    )

    assert response["status"] == "COMMITTED"
    assert response["distanceMeters"] == 42000.0

    client._session.call_tool.assert_awaited_once_with(
        "recalculateDetour",
        arguments={
            "vehicleId": "V-004",
            "routeId": "RT-004",
            "originLng": 101.4496,
            "originLat": 3.0430,
            "destLng": 101.7116,
            "destLat": 3.1579,
            "avoidPolygon": avoid_poly,
            "penaltyFactor": 0.0,
            "rationale": "Recalculated avoiding D-001",
            "anomalyId": "D-001",
        },
    )


@pytest.mark.asyncio
async def test_recalculate_detour_rejects_out_of_bounds_coordinates() -> None:
    client = AstraMcpClient()
    client._connected = True
    client.session = AsyncMock()

    with pytest.raises(Exception):
        await client.recalculate_detour(
            vehicle_id="V-004",
            route_id="RT-004",
            origin_lng=100.0,  # Out of bounds (< 101.40)
            origin_lat=3.0430,
            dest_lng=101.7116,
            dest_lat=3.1579,
        )


@pytest.mark.asyncio
async def test_coordinator_propagates_anomaly_id_to_mcp_client() -> None:
    mock_mcp = AsyncMock(spec=AstraMcpClient)
    mock_mcp.update_route.return_value = {
        "status": "COMMITTED",
        "vehicleId": "V-001",
        "routeId": "RT-001",
    }

    coordinator = NegotiationCoordinator(mcp_client=mock_mcp)

    agent_a = VehicleAgent(
        vehicle_id="V-001",
        route_id="RT-001",
        cargo_priority=5,
        speed_kmh=60.0,
        current_location=[101.5810, 3.0820],
    )
    agent_b = VehicleAgent(
        vehicle_id="V-002",
        route_id="RT-002",
        cargo_priority=2,
        speed_kmh=50.0,
        current_location=[101.5830, 3.0815],
    )

    anomaly = Anomaly(
        anomaly_id="D-100",
        anomaly_type="ROAD_CLOSURE",
        severity="HIGH",
        center=[101.6210, 3.1180],
        radius_meters=500.0,
    )

    await coordinator.handle_collision(agent_a, anomaly)
    decision = await coordinator.handle_collision(agent_b, anomaly)

    assert decision is not None
    assert decision.winner_vehicle_id == "V-001"

    # 1 call for initial single-vehicle collision + 2 calls for pairwise negotiation decision
    assert mock_mcp.update_route.await_count == 3
    for call in mock_mcp.update_route.await_args_list:
        assert call.kwargs["anomaly_id"] == "D-100"
