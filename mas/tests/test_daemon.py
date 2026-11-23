"""
Unit tests for the ASTRA MAS daemon entrypoint (Issue #20).
"""
import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from mas.main import run_daemon


@pytest.mark.asyncio
async def test_daemon_starts_and_stops_on_shutdown_event():
    """Verifies that run_daemon connects to MCP, starts monitoring, and shuts down on stop event."""
    shutdown_event = asyncio.Event()

    mock_client = AsyncMock()
    mock_client.is_connected = True
    mock_client.connect = AsyncMock()
    mock_client.disconnect = AsyncMock()

    mock_monitor = AsyncMock()
    mock_monitor.start = MagicMock()
    mock_monitor.stop = AsyncMock()

    with (
        patch("mas.main.AstraMcpClient", return_value=mock_client),
        patch("mas.main.FleetMonitor", return_value=mock_monitor),
        patch("mas.main.NegotiationCoordinator", return_value=AsyncMock()),
    ):
        daemon_task = asyncio.create_task(
            run_daemon(
                server_url="http://localhost:8080/sse",
                poll_interval=0.1,
                shutdown_event=shutdown_event,
            )
        )

        # Allow daemon to initialize and start loop
        await asyncio.sleep(0.1)

        # Trigger shutdown
        shutdown_event.set()
        await asyncio.wait_for(daemon_task, timeout=2.0)

        mock_client.connect.assert_awaited_once()
        mock_monitor.start.assert_called_once()
        mock_monitor.stop.assert_awaited_once()
        mock_client.disconnect.assert_awaited_once()


@pytest.mark.asyncio
async def test_daemon_retries_on_connection_failure():
    """Verifies that run_daemon backs off and retries when initial connection fails."""
    shutdown_event = asyncio.Event()

    mock_client = AsyncMock()
    # First attempt fails, second attempt succeeds
    mock_client.connect = AsyncMock(side_effect=[ConnectionRefusedError("Offline"), None])
    mock_client.is_connected = True
    mock_client.disconnect = AsyncMock()

    mock_monitor = AsyncMock()
    mock_monitor.start = MagicMock()
    mock_monitor.stop = AsyncMock()

    with (
        patch("mas.main.AstraMcpClient", return_value=mock_client),
        patch("mas.main.FleetMonitor", return_value=mock_monitor),
        patch("mas.main.NegotiationCoordinator", return_value=AsyncMock()),
    ):
        daemon_task = asyncio.create_task(
            run_daemon(
                server_url="http://localhost:8080/sse",
                poll_interval=0.1,
                shutdown_event=shutdown_event,
            )
        )

        # Allow time for first failure and subsequent retry
        await asyncio.sleep(2.5)

        # Trigger shutdown
        shutdown_event.set()
        await asyncio.wait_for(daemon_task, timeout=2.0)

        # connect was called at least twice
        assert mock_client.connect.await_count >= 2
        mock_client.disconnect.assert_awaited()
