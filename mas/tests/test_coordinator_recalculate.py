"""
Unit tests for NegotiationCoordinator dynamic detour recalculation and deduplication (Issue #20).
"""
from unittest.mock import AsyncMock

import pytest

from mas.models.anomaly import Anomaly, AnomalyType
from mas.models.coordinates import Coordinates
from mas.models.vehicle_agent import VehicleAgent
from mas.negotiation.coordinator import NegotiationCoordinator


@pytest.fixture
def mock_mcp_client():
    client = AsyncMock()
    client.recalculate_detour = AsyncMock(return_value={
        "status": "COMMITTED",
        "vehicleId": "V-001",
        "routeId": "RT-001",
        "geometry": {
            "type": "LineString",
            "coordinates": [[101.5270, 3.0730], [101.5900, 3.0900], [101.7116, 3.1579]],
        },
        "distanceMeters": 16200.0,
        "estimatedTimeMs": 950000,
    })
    client.update_route = AsyncMock(return_value={
        "status": "COMMITTED",
        "vehicleId": "V-001",
        "routeId": "RT-001",
    })
    return client


@pytest.fixture
def sample_anomaly():
    return Anomaly(
        id="D-TEST-001",
        type=AnomalyType.ROAD_CLOSURE,
        center=Coordinates(longitude=101.5833, latitude=3.0833),
        radius_meters=600.0,
        polygon_coordinates=[
            Coordinates(longitude=101.5800, latitude=3.0800),
            Coordinates(longitude=101.5866, latitude=3.0800),
            Coordinates(longitude=101.5866, latitude=3.0866),
            Coordinates(longitude=101.5800, latitude=3.0866),
            Coordinates(longitude=101.5800, latitude=3.0800),
        ],
    )


@pytest.fixture
def sample_agent():
    return VehicleAgent(
        id="V-001",
        cargoPriority=5,
        currentLocation=Coordinates(longitude=101.5270, latitude=3.0730),
        routeId="RT-001",
        status="NOMINAL",
        upcomingTrajectory=[
            Coordinates(longitude=101.5270, latitude=3.0730),
            Coordinates(longitude=101.5833, latitude=3.0833),
            Coordinates(longitude=101.7116, latitude=3.1579),
        ],
    )


@pytest.mark.asyncio
async def test_single_vehicle_triggers_dynamic_recalculate_detour(mock_mcp_client, sample_anomaly, sample_agent):
    """Verifies that a single disrupted vehicle triggers dynamic recalculate_detour()."""
    coordinator = NegotiationCoordinator(mcp_client=mock_mcp_client)

    await coordinator.handle_collision(sample_agent, sample_anomaly)

    mock_mcp_client.recalculate_detour.assert_awaited_once()
    call_kwargs = mock_mcp_client.recalculate_detour.call_args.kwargs
    assert call_kwargs["vehicle_id"] == "V-001"
    assert call_kwargs["route_id"] == "RT-001"
    assert call_kwargs["anomaly_id"] == "D-TEST-001"
    assert call_kwargs["penalty_factor"] == 0.0
    assert sample_agent.status == "REROUTING"


@pytest.mark.asyncio
async def test_resolved_conflict_deduplication_guard(mock_mcp_client, sample_anomaly, sample_agent):
    """Verifies that subsequent collisions with the same anomaly are skipped to prevent spam."""
    coordinator = NegotiationCoordinator(mcp_client=mock_mcp_client)

    # First call
    await coordinator.handle_collision(sample_agent, sample_anomaly)
    assert mock_mcp_client.recalculate_detour.await_count == 1

    # Second call in subsequent poll cycle
    await coordinator.handle_collision(sample_agent, sample_anomaly)
    # Should still only be 1 call due to in-flight/resolved guard
    assert mock_mcp_client.recalculate_detour.await_count == 1


@pytest.mark.asyncio
async def test_clear_conflict_resets_guard(mock_mcp_client, sample_anomaly, sample_agent):
    """Verifies that clearing an anomaly resets the deduplication guard."""
    coordinator = NegotiationCoordinator(mcp_client=mock_mcp_client)

    await coordinator.handle_collision(sample_agent, sample_anomaly)
    assert mock_mcp_client.recalculate_detour.await_count == 1

    # Clear conflict tracking for the anomaly
    coordinator.clear_conflict("D-TEST-001")

    # Now it should be allowed to trigger again if re-injected
    await coordinator.handle_collision(sample_agent, sample_anomaly)
    assert mock_mcp_client.recalculate_detour.await_count == 2


@pytest.mark.asyncio
async def test_dynamic_recalculate_fallback_to_corridor(mock_mcp_client, sample_anomaly, sample_agent):
    """Verifies fallback to update_route when GraphHopper recalculate fails."""
    mock_mcp_client.recalculate_detour.side_effect = RuntimeError("GraphHopper unavailable")
    coordinator = NegotiationCoordinator(mcp_client=mock_mcp_client)

    await coordinator.handle_collision(sample_agent, sample_anomaly)

    # recalculate was tried and failed
    mock_mcp_client.recalculate_detour.assert_awaited_once()
    # update_route was called as fallback
    mock_mcp_client.update_route.assert_awaited_once()
    call_kwargs = mock_mcp_client.update_route.call_args.kwargs
    assert call_kwargs["vehicle_id"] == "V-001"
    assert sample_agent.status == "REROUTING"
