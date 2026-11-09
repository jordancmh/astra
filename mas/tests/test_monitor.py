"""
Unit and integration tests for FleetMonitor (Issue #15, Task 4.1).
Validates active polling via AstraMcpClient, trajectory intersection triggers,
state transitions to INTERSECTED, and async background task lifecycle.
"""
import asyncio
from unittest.mock import AsyncMock

import pytest

from mas.detection.monitor import FleetMonitor
from mas.models.anomaly import Anomaly, AnomalySeverity, AnomalyType
from mas.models.coordinates import Coordinates
from mas.models.vehicle_agent import VehicleAgent


@pytest.fixture
def mock_mcp_client():
    client = AsyncMock()
    client.get_fleet_state = AsyncMock(return_value={"status": "SUCCESS", "count": 0, "vehicles": []})
    return client


def test_monitor_initialization(mock_mcp_client):
    """Verifies initial monitor state and registry."""
    monitor = FleetMonitor(mcp_client=mock_mcp_client, poll_interval_seconds=2.0)
    assert monitor.mcp_client == mock_mcp_client
    assert monitor.poll_interval_seconds == 2.0
    assert len(monitor.agents) == 0
    assert len(monitor.active_anomalies) == 0
    assert not monitor.is_running


def test_monitor_register_and_remove_anomaly(mock_mcp_client):
    """Verifies registering and removing anomalies, and clearing associated agent flags."""
    monitor = FleetMonitor(mcp_client=mock_mcp_client)
    anomaly = Anomaly(
        anomaly_id="D-PJ-001",
        anomaly_type=AnomalyType.ROAD_CLOSURE,
        center=Coordinates(longitude=101.6200, latitude=3.1100),
        radius_meters=300.0,
    )
    agent = VehicleAgent(
        vehicle_id="V-004",
        cargo_priority=5,
        current_location=Coordinates(longitude=101.6200, latitude=3.1100),
        status="INTERSECTED",
        intersected_anomaly_id="D-PJ-001",
    )
    monitor.register_agent(agent)
    monitor.register_anomaly(anomaly)
    assert "D-PJ-001" in monitor.active_anomalies

    # Removing the anomaly should reset agent state to NOMINAL
    monitor.remove_anomaly("D-PJ-001")
    assert "D-PJ-001" not in monitor.active_anomalies
    assert agent.status == "NOMINAL"
    assert agent.intersected_anomaly_id is None


def test_monitor_clear_anomalies(mock_mcp_client):
    """Verifies clear_anomalies resets all tracked disruptions and agent states."""
    monitor = FleetMonitor(mcp_client=mock_mcp_client)
    anomaly = Anomaly(
        anomaly_id="D-PJ-002",
        anomaly_type=AnomalyType.CONGESTION,
        center=Coordinates(longitude=101.6200, latitude=3.1100),
    )
    agent = VehicleAgent(
        vehicle_id="V-005",
        cargo_priority=3,
        current_location=Coordinates(longitude=101.6200, latitude=3.1100),
        status="INTERSECTED",
        intersected_anomaly_id="D-PJ-002",
    )
    monitor.register_agent(agent)
    monitor.register_anomaly(anomaly)

    monitor.clear_anomalies()
    assert len(monitor.active_anomalies) == 0
    assert agent.status == "NOMINAL"
    assert agent.intersected_anomaly_id is None


@pytest.mark.asyncio
async def test_monitor_poll_once_no_intersection(mock_mcp_client):
    """Verifies that an agent with a clear trajectory remains NOMINAL."""
    # Agent trajectory completely bypasses anomaly
    agent = VehicleAgent(
        vehicle_id="V-001",
        cargo_priority=3,
        current_location=Coordinates(longitude=101.5000, latitude=3.0000),
        upcoming_trajectory=[
            Coordinates(longitude=101.5000, latitude=3.0000),
            Coordinates(longitude=101.5200, latitude=3.0200),
        ],
    )
    # Anomaly in Petaling Jaya (~15km away)
    anomaly = Anomaly(
        anomaly_id="D-001",
        anomaly_type=AnomalyType.ACCIDENT,
        center=Coordinates(longitude=101.6200, latitude=3.1100),
        radius_meters=400.0,
    )
    mock_mcp_client.get_fleet_state = AsyncMock(return_value={
        "status": "SUCCESS",
        "vehicles": [
            {
                "vehicleId": "V-001",
                "longitude": 101.5000,
                "latitude": 3.0000,
                "speedKmh": 60.0,
                "bearing": 45.0,
            }
        ],
    })

    monitor = FleetMonitor(mcp_client=mock_mcp_client)
    monitor.register_agent(agent)
    monitor.register_anomaly(anomaly)

    collisions = await monitor.poll_once()
    assert len(collisions) == 0
    assert agent.status == "NOMINAL"
    assert agent.intersected_anomaly_id is None
    assert agent.speed_kmh == 60.0


@pytest.mark.asyncio
async def test_monitor_poll_once_with_collision(mock_mcp_client):
    """Verifies that an intersecting trajectory triggers the INTERSECTED state."""
    # Agent heading directly through anomaly
    agent = VehicleAgent(
        vehicle_id="V-004",
        cargo_priority=5,
        current_location=Coordinates(longitude=101.6000, latitude=3.1100),
        upcoming_trajectory=[
            Coordinates(longitude=101.6000, latitude=3.1100),
            Coordinates(longitude=101.6300, latitude=3.1100),
        ],
    )
    anomaly = Anomaly(
        anomaly_id="D-002",
        anomaly_type=AnomalyType.ROAD_CLOSURE,
        center=Coordinates(longitude=101.6150, latitude=3.1100),
        radius_meters=500.0,
    )

    monitor = FleetMonitor(mcp_client=mock_mcp_client)
    monitor.register_agent(agent)
    monitor.register_anomaly(anomaly)

    collisions = await monitor.poll_once()
    assert len(collisions) == 1
    assert collisions[0] == ("V-004", "D-002")
    assert agent.status == "INTERSECTED"
    assert agent.intersected_anomaly_id == "D-002"


@pytest.mark.asyncio
async def test_monitor_collision_callback_invoked(mock_mcp_client):
    """Verifies that the on_collision callback is awaited with agent and anomaly."""
    callback_mock = AsyncMock()

    agent = VehicleAgent(
        vehicle_id="V-004",
        cargo_priority=5,
        current_location=Coordinates(longitude=101.6100, latitude=3.1100),
        upcoming_trajectory=[
            Coordinates(longitude=101.6100, latitude=3.1100),
            Coordinates(longitude=101.6200, latitude=3.1100),
        ],
    )
    anomaly = Anomaly(
        anomaly_id="D-003",
        anomaly_type=AnomalyType.WEATHER,
        center=Coordinates(longitude=101.6150, latitude=3.1100),
        radius_meters=800.0,
    )

    monitor = FleetMonitor(
        mcp_client=mock_mcp_client,
        on_collision=callback_mock,
    )
    monitor.register_agent(agent)
    monitor.register_anomaly(anomaly)

    await monitor.poll_once()
    callback_mock.assert_awaited_once_with(agent, anomaly)


@pytest.mark.asyncio
async def test_monitor_syncs_anomalies_from_payload(mock_mcp_client):
    """Verifies that anomalies in the getFleetState payload are automatically registered."""
    mock_mcp_client.get_fleet_state = AsyncMock(return_value={
        "status": "SUCCESS",
        "vehicles": [],
        "anomalies": [
            {
                "anomalyId": "D-AUTO-01",
                "anomalyType": "CONGESTION",
                "severity": "HIGH",
                "center": [101.6250, 3.1150],
                "radiusMeters": 600.0,
            }
        ],
    })

    monitor = FleetMonitor(mcp_client=mock_mcp_client)
    await monitor.poll_once()
    assert "D-AUTO-01" in monitor.active_anomalies
    assert monitor.active_anomalies["D-AUTO-01"].anomaly_type == AnomalyType.CONGESTION


@pytest.mark.asyncio
async def test_monitor_handles_mcp_client_error_resiliently(mock_mcp_client):
    """Verifies that an MCP client network failure logs a warning without crashing."""
    mock_mcp_client.get_fleet_state = AsyncMock(side_effect=RuntimeError("Connection refused"))

    monitor = FleetMonitor(mcp_client=mock_mcp_client)
    collisions = await monitor.poll_once()
    assert collisions == []


@pytest.mark.asyncio
async def test_monitor_background_task_lifecycle(mock_mcp_client):
    """Verifies background asyncio task startup and graceful cancellation."""
    monitor = FleetMonitor(mcp_client=mock_mcp_client, poll_interval_seconds=0.05)

    assert not monitor.is_running
    monitor.start()
    assert monitor.is_running

    # Let the loop run several cycles
    await asyncio.sleep(0.15)
    assert mock_mcp_client.get_fleet_state.await_count >= 2

    await monitor.stop()
    assert not monitor.is_running


@pytest.mark.asyncio
async def test_monitor_async_context_manager(mock_mcp_client):
    """Verifies async with FleetMonitor() context manager lifecycle."""
    async with FleetMonitor(mcp_client=mock_mcp_client, poll_interval_seconds=0.05) as monitor:
        assert monitor.is_running
        await asyncio.sleep(0.1)

    assert not monitor.is_running
