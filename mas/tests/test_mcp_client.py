"""
Unit and integration tests for AstraMcpClient (Issue #14).
Validates async session lifecycle, SSE connection management, tool discovery,
and typed tool execution with mocked transport layers.
"""
import os
from unittest.mock import AsyncMock, patch

import pytest
from pydantic import ValidationError
from mcp import types

from mas.mcp_client import AstraMcpClient


def test_client_initialization_default_url():
    """Verifies default URL and timeout assignment."""
    client = AstraMcpClient()
    assert client.server_url == "http://localhost:8080/sse"
    assert client.timeout == 10.0
    assert not client.is_connected


def test_client_initialization_custom_url():
    """Verifies custom server URL and timeout assignment."""
    client = AstraMcpClient(server_url="http://localhost:8081/sse", timeout=15.0)
    assert client.server_url == "http://localhost:8081/sse"
    assert client.timeout == 15.0
    assert not client.is_connected


def test_client_initialization_env_var(monkeypatch):
    """Verifies MCP_SERVER_URL environment variable override."""
    monkeypatch.setenv("MCP_SERVER_URL", "http://mcp-backend:9090/sse")
    client = AstraMcpClient()
    assert client.server_url == "http://mcp-backend:9090/sse"


@pytest.mark.asyncio
async def test_client_not_connected_raises_runtime_error():
    """Verifies calling operations before connecting raises RuntimeError."""
    client = AstraMcpClient()
    with pytest.raises(RuntimeError, match="not connected"):
        await client.list_tools()

    with pytest.raises(RuntimeError, match="not connected"):
        await client.call_tool("getFleetState")

    with pytest.raises(RuntimeError, match="not connected"):
        await client.get_fleet_state()

    with pytest.raises(RuntimeError, match="not connected"):
        await client.update_route(
            vehicle_id="V-001",
            route_id="RT-001",
            coordinates=[[101.50, 3.00], [101.60, 3.10]],
        )


@pytest.mark.asyncio
async def test_client_connect_and_disconnect_mocked():
    """Verifies connect and disconnect properly manage the session lifecycle."""
    client = AstraMcpClient("http://localhost:8080/sse")
    mock_session = AsyncMock()
    mock_session.initialize = AsyncMock()

    with patch("mas.mcp_client.sse_client") as mock_sse, patch("mas.mcp_client.ClientSession") as mock_cs:
        mock_sse.return_value.__aenter__.return_value = (AsyncMock(), AsyncMock())
        mock_sse.return_value.__aexit__.return_value = None
        mock_cs.return_value.__aenter__.return_value = mock_session
        mock_cs.return_value.__aexit__.return_value = None

        session = await client.connect()
        assert client.is_connected
        assert session == mock_session
        mock_session.initialize.assert_awaited_once()

        # Idempotent connect
        second_session = await client.connect()
        assert second_session == mock_session

        await client.disconnect()
        assert not client.is_connected


@pytest.mark.asyncio
async def test_client_async_context_manager_mocked():
    """Verifies async with AstraMcpClient() lifecycle."""
    mock_session = AsyncMock()
    mock_session.initialize = AsyncMock()

    with patch("mas.mcp_client.sse_client") as mock_sse, patch("mas.mcp_client.ClientSession") as mock_cs:
        mock_sse.return_value.__aenter__.return_value = (AsyncMock(), AsyncMock())
        mock_sse.return_value.__aexit__.return_value = None
        mock_cs.return_value.__aenter__.return_value = mock_session
        mock_cs.return_value.__aexit__.return_value = None

        async with AstraMcpClient() as client:
            assert client.is_connected

        assert not client.is_connected


@pytest.mark.asyncio
async def test_client_connect_failure_cleans_up():
    """Verifies failed connection properly resets state and re-raises."""
    client = AstraMcpClient()

    with patch("mas.mcp_client.sse_client", side_effect=ConnectionRefusedError("Server down")):
        with pytest.raises(ConnectionRefusedError):
            await client.connect()

        assert not client.is_connected


@pytest.mark.asyncio
async def test_client_list_tools_mocked():
    """Verifies tool discovery returns tools including getFleetState and updateRoute."""
    client = AstraMcpClient()
    mock_session = AsyncMock()
    mock_session.initialize = AsyncMock()
    expected_tools = [
        types.Tool(name="getFleetState", description="Get fleet telemetry", inputSchema={}),
        types.Tool(name="updateRoute", description="Update vehicle route", inputSchema={}),
    ]
    mock_session.list_tools = AsyncMock(return_value=types.ListToolsResult(tools=expected_tools))

    with patch("mas.mcp_client.sse_client") as mock_sse, patch("mas.mcp_client.ClientSession") as mock_cs:
        mock_sse.return_value.__aenter__.return_value = (AsyncMock(), AsyncMock())
        mock_sse.return_value.__aexit__.return_value = None
        mock_cs.return_value.__aenter__.return_value = mock_session
        mock_cs.return_value.__aexit__.return_value = None

        async with client:
            tools = await client.list_tools()
            assert len(tools) == 2
            assert tools[0].name == "getFleetState"
            assert tools[1].name == "updateRoute"


@pytest.mark.asyncio
async def test_client_get_fleet_state_mocked():
    """Verifies get_fleet_state tool call and JSON parsing."""
    client = AstraMcpClient()
    mock_session = AsyncMock()
    mock_session.initialize = AsyncMock()
    fleet_json = '{"status":"SUCCESS","count":1,"vehicles":[{"vehicleId":"V-001","status":"NOMINAL"}]}'
    mock_session.call_tool = AsyncMock(return_value=types.CallToolResult(
        content=[types.TextContent(type="text", text=fleet_json)],
        is_error=False,
    ))

    with patch("mas.mcp_client.sse_client") as mock_sse, patch("mas.mcp_client.ClientSession") as mock_cs:
        mock_sse.return_value.__aenter__.return_value = (AsyncMock(), AsyncMock())
        mock_sse.return_value.__aexit__.return_value = None
        mock_cs.return_value.__aenter__.return_value = mock_session
        mock_cs.return_value.__aexit__.return_value = None

        async with client:
            # Query all vehicles
            result = await client.get_fleet_state()
            assert result["status"] == "SUCCESS"
            assert result["count"] == 1
            assert result["vehicles"][0]["vehicleId"] == "V-001"
            mock_session.call_tool.assert_awaited_with("getFleetState", arguments={})

            # Query specific vehicle
            await client.get_fleet_state(vehicle_id="V-001")
            mock_session.call_tool.assert_awaited_with("getFleetState", arguments={"vehicleId": "V-001"})


@pytest.mark.asyncio
async def test_client_update_route_mocked():
    """Verifies update_route tool call with coordinates and optional rationale."""
    client = AstraMcpClient()
    mock_session = AsyncMock()
    mock_session.initialize = AsyncMock()
    update_json = '{"routeId":"RT-001","status":"ACCEPTED","distanceMeters":1520.5}'
    mock_session.call_tool = AsyncMock(return_value=types.CallToolResult(
        content=[types.TextContent(type="text", text=update_json)],
        is_error=False,
    ))

    with patch("mas.mcp_client.sse_client") as mock_sse, patch("mas.mcp_client.ClientSession") as mock_cs:
        mock_sse.return_value.__aenter__.return_value = (AsyncMock(), AsyncMock())
        mock_sse.return_value.__aexit__.return_value = None
        mock_cs.return_value.__aenter__.return_value = mock_session
        mock_cs.return_value.__aexit__.return_value = None

        coords = [[101.5000, 3.0000], [101.5500, 3.0500], [101.6000, 3.1000]]
        async with client:
            result = await client.update_route(
                vehicle_id="V-001",
                route_id="RT-001",
                coordinates=coords,
                rationale="Detour around flood",
            )
            assert result["routeId"] == "RT-001"
            assert result["status"] == "ACCEPTED"
            mock_session.call_tool.assert_awaited_with(
                "updateRoute",
                arguments={
                    "vehicleId": "V-001",
                    "routeId": "RT-001",
                    "coordinates": coords,
                    "rationale": "Detour around flood",
                },
            )


@pytest.mark.asyncio
async def test_client_update_route_validation():
    """Verifies update_route validates vehicle_id, route_id, and coordinates."""
    client = AstraMcpClient()

    # Empty vehicle_id
    with pytest.raises(ValueError, match="vehicle_id"):
        await client.update_route("", "RT-001", [[101.50, 3.00], [101.60, 3.10]])

    # Empty route_id
    with pytest.raises(ValueError, match="route_id"):
        await client.update_route("V-001", "   ", [[101.50, 3.00], [101.60, 3.10]])

    # Fewer than 2 points
    with pytest.raises(ValueError, match="coordinates must contain at least 2 points"):
        await client.update_route("V-001", "RT-001", [[101.50, 3.00]])

    # Out of Klang Valley bounds (longitude too low)
    with pytest.raises(ValidationError):
        await client.update_route("V-001", "RT-001", [[101.20, 3.00], [101.60, 3.10]])

    # Inverted coordinates [latitude, longitude] must fail validation
    with pytest.raises(ValidationError):
        await client.update_route("V-001", "RT-001", [[3.00, 101.50], [3.10, 101.60]])


@pytest.mark.asyncio
async def test_client_call_tool_error_handling():
    """Verifies RuntimeError is raised when tool execution returns is_error=True."""
    client = AstraMcpClient()
    mock_session = AsyncMock()
    mock_session.initialize = AsyncMock()
    mock_session.call_tool = AsyncMock(return_value=types.CallToolResult(
        content=[types.TextContent(type="text", text="Vehicle V-999 not found")],
        is_error=True,
    ))

    with patch("mas.mcp_client.sse_client") as mock_sse, patch("mas.mcp_client.ClientSession") as mock_cs:
        mock_sse.return_value.__aenter__.return_value = (AsyncMock(), AsyncMock())
        mock_sse.return_value.__aexit__.return_value = None
        mock_cs.return_value.__aenter__.return_value = mock_session
        mock_cs.return_value.__aexit__.return_value = None

        async with client:
            with pytest.raises(RuntimeError, match="Vehicle V-999 not found"):
                await client.get_fleet_state(vehicle_id="V-999")


@pytest.mark.asyncio
async def test_client_call_tool_non_json_response():
    """Verifies non-JSON plain text tool responses are wrapped under raw key."""
    client = AstraMcpClient()
    mock_session = AsyncMock()
    mock_session.initialize = AsyncMock()
    mock_session.call_tool = AsyncMock(return_value=types.CallToolResult(
        content=[types.TextContent(type="text", text="Plain text response")],
        is_error=False,
    ))

    with patch("mas.mcp_client.sse_client") as mock_sse, patch("mas.mcp_client.ClientSession") as mock_cs:
        mock_sse.return_value.__aenter__.return_value = (AsyncMock(), AsyncMock())
        mock_sse.return_value.__aexit__.return_value = None
        mock_cs.return_value.__aenter__.return_value = mock_session
        mock_cs.return_value.__aexit__.return_value = None

        async with client:
            result = await client.call_tool("customTool")
            assert result == {"raw": "Plain text response"}
