"""
Unit and integration tests for conflict negotiation protocols (Issue #16).
Validates right-of-way determination, 3-tier tie-breakers (priority -> speed -> vehicle_id),
detour corridor allocations, MCP update_route integration, and FleetMonitor hooks.
"""
from unittest.mock import AsyncMock

import pytest
from pydantic import ValidationError

from mas.detection.monitor import FleetMonitor
from mas.models.anomaly import Anomaly, AnomalyType
from mas.models.coordinates import Coordinates
from mas.models.vehicle_agent import VehicleAgent
from mas.negotiation import (
    DEFAULT_PRIMARY_CORRIDOR,
    DEFAULT_SECONDARY_CORRIDOR,
    CorridorType,
    DetourCorridor,
    NegotiationCoordinator,
    RightOfWayDecision,
    RightOfWayNegotiator,
)


def test_corridor_validation_valid():
    """Verifies valid DetourCorridor creation with [longitude, latitude] waypoints."""
    corridor = DetourCorridor(
        corridor_id="CORR-TEST-01",
        name="Test Corridor",
        corridor_type=CorridorType.PRIMARY,
        waypoints=[
            Coordinates(longitude=101.5000, latitude=3.0000),
            Coordinates(longitude=101.6000, latitude=3.1000),
        ],
        distance_meters=15000.0,
    )
    coords = corridor.to_coordinates_array()
    assert coords == [[101.5000, 3.0000], [101.6000, 3.1000]]


def test_corridor_validation_out_of_bounds():
    """Verifies that waypoints outside the Klang Valley bounding box raise ValidationError."""
    with pytest.raises(ValidationError):
        DetourCorridor(
            corridor_id="CORR-INVALID",
            name="Invalid Bounds Corridor",
            waypoints=[
                Coordinates(longitude=101.2000, latitude=3.0000),  # Longitude < 101.40
                Coordinates(longitude=101.6000, latitude=3.1000),
            ],
        )


def test_corridor_validation_fewer_than_two_points():
    """Verifies that a corridor with fewer than 2 waypoints is rejected."""
    with pytest.raises(ValidationError):
        DetourCorridor(
            corridor_id="CORR-ONE-PT",
            name="Single Point Corridor",
            waypoints=[
                Coordinates(longitude=101.5000, latitude=3.0000),
            ],
        )


def test_negotiate_pair_higher_cargo_priority_wins():
    """Tier 1: Higher cargo_priority (1 to 5) wins right-of-way (FR 3.2)."""
    negotiator = RightOfWayNegotiator()

    # V-004: Priority 5 (Medical, 45 km/h)
    agent_medical = VehicleAgent(
        vehicle_id="V-004",
        cargo_priority=5,
        speed_kmh=45.0,
        current_location=Coordinates(longitude=101.5810, latitude=3.0820),
    )
    # V-005: Priority 2 (Retail, 60 km/h)
    agent_retail = VehicleAgent(
        vehicle_id="V-005",
        cargo_priority=2,
        speed_kmh=60.0,
        current_location=Coordinates(longitude=101.5830, latitude=3.0815),
    )

    decision = negotiator.negotiate_pair(
        agent_a=agent_medical,
        agent_b=agent_retail,
        anomaly_id="D-001",
    )

    assert decision.winner_vehicle_id == "V-004"
    assert decision.yielding_vehicle_id == "V-005"
    assert decision.tie_breaker_reason == "cargo_priority"
    assert decision.assigned_corridors["V-004"].corridor_type == CorridorType.PRIMARY
    assert decision.assigned_corridors["V-005"].corridor_type == CorridorType.SECONDARY
    assert "V-004 granted primary detour" in decision.rationale
    assert "V-005 yielded to" in decision.rationale


def test_negotiate_pair_equal_priority_speed_tie_breaker():
    """Tier 2: When cargo priorities match, higher speed_kmh preserves momentum and wins."""
    negotiator = RightOfWayNegotiator()

    # Both priority 3
    agent_fast = VehicleAgent(
        vehicle_id="V-001",
        cargo_priority=3,
        speed_kmh=75.0,
        current_location=Coordinates(longitude=101.6000, latitude=3.1000),
    )
    agent_slow = VehicleAgent(
        vehicle_id="V-002",
        cargo_priority=3,
        speed_kmh=40.0,
        current_location=Coordinates(longitude=101.6000, latitude=3.1000),
    )

    decision = negotiator.negotiate_pair(
        agent_a=agent_fast,
        agent_b=agent_slow,
        anomaly_id="D-002",
    )

    assert decision.winner_vehicle_id == "V-001"
    assert decision.yielding_vehicle_id == "V-002"
    assert decision.tie_breaker_reason == "speed_kmh"
    assert "Higher momentum" in decision.rationale


def test_negotiate_pair_equal_priority_equal_speed_id_tie_breaker():
    """Tier 3: When priority and speed match, alphabetical vehicle_id breaks tie deterministically."""
    negotiator = RightOfWayNegotiator()

    # Both priority 4 and speed 55 km/h
    agent_a = VehicleAgent(
        vehicle_id="V-001",
        cargo_priority=4,
        speed_kmh=55.0,
        current_location=Coordinates(longitude=101.6000, latitude=3.1000),
    )
    agent_b = VehicleAgent(
        vehicle_id="V-002",
        cargo_priority=4,
        speed_kmh=55.0,
        current_location=Coordinates(longitude=101.6000, latitude=3.1000),
    )

    decision = negotiator.negotiate_pair(
        agent_a=agent_a,
        agent_b=agent_b,
        anomaly_id="D-003",
    )

    # 'V-001' < 'V-002'
    assert decision.winner_vehicle_id == "V-001"
    assert decision.yielding_vehicle_id == "V-002"
    assert decision.tie_breaker_reason == "vehicle_id"
    assert "Consensus identifier precedence" in decision.rationale


def test_negotiate_group_multiple_vehicles():
    """Verifies multi-vehicle group negotiation ranks agents accurately."""
    negotiator = RightOfWayNegotiator()

    agents = [
        VehicleAgent(
            vehicle_id="V-003",
            cargo_priority=1,
            speed_kmh=50.0,
            current_location=Coordinates(longitude=101.6000, latitude=3.1000),
        ),
        VehicleAgent(
            vehicle_id="V-001",
            cargo_priority=5,
            speed_kmh=40.0,
            current_location=Coordinates(longitude=101.6000, latitude=3.1000),
        ),
        VehicleAgent(
            vehicle_id="V-002",
            cargo_priority=3,
            speed_kmh=60.0,
            current_location=Coordinates(longitude=101.6000, latitude=3.1000),
        ),
    ]

    decisions = negotiator.negotiate_group(agents=agents, anomaly_id="D-004")
    assert len(decisions) == 2

    # First decision: V-001 (P5) beats V-002 (P3)
    assert decisions[0].winner_vehicle_id == "V-001"
    assert decisions[0].yielding_vehicle_id == "V-002"

    # Second decision: V-002 (P3) beats V-003 (P1)
    assert decisions[1].winner_vehicle_id == "V-002"
    assert decisions[1].yielding_vehicle_id == "V-003"


@pytest.mark.asyncio
async def test_coordinator_single_vehicle_collision():
    """Verifies that a single colliding vehicle receives the primary detour directly."""
    mock_mcp = AsyncMock()
    mock_mcp.update_route = AsyncMock(return_value={"status": "ACCEPTED"})

    coordinator = NegotiationCoordinator(mcp_client=mock_mcp)
    agent = VehicleAgent(
        vehicle_id="V-001",
        cargo_priority=3,
        route_id="RT-001",
        current_location=Coordinates(longitude=101.6000, latitude=3.1000),
    )
    anomaly = Anomaly(
        anomaly_id="D-001",
        anomaly_type=AnomalyType.ROAD_CLOSURE,
        center=Coordinates(longitude=101.6100, latitude=3.1100),
    )

    decision = await coordinator.handle_collision(agent=agent, anomaly=anomaly)
    # None returned for single vehicle (no negotiation needed yet)
    assert decision is None
    assert agent.status == "REROUTING"

    # MCP update_route was called with primary corridor
    mock_mcp.update_route.assert_awaited_once()
    call_args = mock_mcp.update_route.await_args.kwargs
    assert call_args["vehicle_id"] == "V-001"
    assert call_args["route_id"] == "RT-001"
    assert call_args["coordinates"] == DEFAULT_PRIMARY_CORRIDOR.to_coordinates_array()


@pytest.mark.asyncio
async def test_coordinator_multi_vehicle_collision_triggers_negotiation():
    """Verifies that 2 contending vehicles trigger negotiation and both receive MCP updates."""
    mock_mcp = AsyncMock()
    mock_mcp.update_route = AsyncMock(return_value={"status": "ACCEPTED"})

    coordinator = NegotiationCoordinator(mcp_client=mock_mcp)
    anomaly = Anomaly(
        anomaly_id="D-002",
        anomaly_type=AnomalyType.ACCIDENT,
        center=Coordinates(longitude=101.6100, latitude=3.1100),
    )

    agent_a = VehicleAgent(
        vehicle_id="V-004",
        cargo_priority=5,
        route_id="RT-004",
        current_location=Coordinates(longitude=101.5810, latitude=3.0820),
    )
    agent_b = VehicleAgent(
        vehicle_id="V-005",
        cargo_priority=2,
        route_id="RT-005",
        current_location=Coordinates(longitude=101.5830, latitude=3.0815),
    )

    # First vehicle collides
    await coordinator.handle_collision(agent=agent_a, anomaly=anomaly)
    assert mock_mcp.update_route.await_count == 1

    # Second vehicle collides with same anomaly -> triggers negotiation
    decision = await coordinator.handle_collision(agent=agent_b, anomaly=anomaly)
    assert decision is not None
    assert decision.winner_vehicle_id == "V-004"
    assert decision.yielding_vehicle_id == "V-005"

    # Both vehicles should have received update_route calls (1 + 2 = 3 calls total)
    assert mock_mcp.update_route.await_count == 3
    assert len(coordinator.get_decisions()) == 1

    # Verify yielding agent received secondary corridor
    yielding_call = mock_mcp.update_route.await_args_list[-1].kwargs
    assert yielding_call["vehicle_id"] == "V-005"
    assert yielding_call["coordinates"] == DEFAULT_SECONDARY_CORRIDOR.to_coordinates_array()


@pytest.mark.asyncio
async def test_coordinator_strictly_valid_coordinates():
    """Verifies that all coordinates passed to update_route are strictly valid [lng, lat] in Klang Valley."""
    mock_mcp = AsyncMock()
    mock_mcp.update_route = AsyncMock(return_value={"status": "ACCEPTED"})

    coordinator = NegotiationCoordinator(mcp_client=mock_mcp)
    agent = VehicleAgent(
        vehicle_id="V-001",
        cargo_priority=3,
        current_location=Coordinates(longitude=101.6000, latitude=3.1000),
    )
    anomaly = Anomaly(
        anomaly_id="D-001",
        anomaly_type=AnomalyType.ROAD_CLOSURE,
        center=Coordinates(longitude=101.6100, latitude=3.1100),
    )

    await coordinator.handle_collision(agent, anomaly)
    coords = mock_mcp.update_route.await_args.kwargs["coordinates"]

    for pt in coords:
        assert len(pt) == 2
        lng, lat = pt[0], pt[1]
        assert 101.40 <= lng <= 101.90
        assert 2.90 <= lat <= 3.30


@pytest.mark.asyncio
async def test_fleet_monitor_hook_integration():
    """End-to-end integration: FleetMonitor polling detects collision and triggers NegotiationCoordinator."""
    mock_mcp = AsyncMock()
    mock_mcp.get_fleet_state = AsyncMock(return_value={
        "status": "SUCCESS",
        "vehicles": [],
    })
    mock_mcp.update_route = AsyncMock(return_value={"status": "ACCEPTED"})

    coordinator = NegotiationCoordinator(mcp_client=mock_mcp)
    anomaly = Anomaly(
        anomaly_id="D-001",
        anomaly_type=AnomalyType.ROAD_CLOSURE,
        center=Coordinates(longitude=101.6150, latitude=3.1100),
        radius_meters=500.0,
    )

    monitor = FleetMonitor(
        mcp_client=mock_mcp,
        on_collision=coordinator.handle_collision,
    )

    agent_medical = VehicleAgent(
        vehicle_id="V-004",
        cargo_priority=5,
        current_location=Coordinates(longitude=101.6100, latitude=3.1100),
        upcoming_trajectory=[
            Coordinates(longitude=101.6100, latitude=3.1100),
            Coordinates(longitude=101.6200, latitude=3.1100),
        ],
    )
    agent_retail = VehicleAgent(
        vehicle_id="V-005",
        cargo_priority=2,
        current_location=Coordinates(longitude=101.6120, latitude=3.1100),
        upcoming_trajectory=[
            Coordinates(longitude=101.6120, latitude=3.1100),
            Coordinates(longitude=101.6180, latitude=3.1100),
        ],
    )

    monitor.register_agent(agent_medical)
    monitor.register_agent(agent_retail)
    monitor.register_anomaly(anomaly)

    # Execute single poll cycle
    collisions = await monitor.poll_once()
    assert len(collisions) == 2

    # Verification: NegotiationCoordinator resolved the conflict and logged the decision
    decisions = coordinator.get_decisions()
    assert len(decisions) == 1
    assert decisions[0].winner_vehicle_id == "V-004"
    assert decisions[0].yielding_vehicle_id == "V-005"
    assert agent_medical.status == "REROUTING"
    assert agent_retail.status == "REROUTING"
