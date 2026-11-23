"""
ASTRA Multi-Agent System (MAS) - Autonomous Negotiation & Rerouting Daemon.
Entrypoint for the background MAS orchestrator connecting to the Spring Boot MCP broker (FR 3.1, FR 3.2, FR 3.3).
"""
import asyncio
import logging
import os
import signal
import sys
from typing import Any

from dotenv import load_dotenv

from mas.detection.monitor import FleetMonitor
from mas.mcp_client import AstraMcpClient
from mas.negotiation.coordinator import NegotiationCoordinator

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("mas.daemon")


async def run_daemon(
    server_url: str | None = None,
    poll_interval: float | None = None,
    shutdown_event: asyncio.Event | None = None,
) -> None:
    """
    Main asynchronous lifecycle loop for the ASTRA MAS daemon.
    Manages client connection, background monitoring, and graceful termination.
    """
    url = server_url or os.getenv("MCP_SERVER_URL", AstraMcpClient.DEFAULT_URL)
    interval = (
        poll_interval
        if poll_interval is not None
        else float(os.getenv("POLL_INTERVAL", "2.0"))
    )

    logger.info("Initializing ASTRA MAS Daemon targeting MCP broker: %s", url)
    logger.info("Fleet monitoring poll interval: %.2f seconds", interval)

    stop_event = shutdown_event or asyncio.Event()

    # Reconnect loop with exponential backoff
    backoff = 2.0
    max_backoff = 30.0

    while not stop_event.is_set():
        client = AstraMcpClient(server_url=url)
        try:
            logger.info("Attempting connection to Spring Boot MCP broker at %s...", url)
            await client.connect()
            logger.info("Successfully connected to MCP broker. Spinning up agents...")
            backoff = 2.0  # Reset backoff on successful connection

            coordinator = NegotiationCoordinator(mcp_client=client)
            monitor = FleetMonitor(
                mcp_client=client,
                poll_interval_seconds=interval,
                on_collision=coordinator.handle_collision,
                on_anomaly_cleared=coordinator.clear_conflict,
            )

            # Start active monitoring
            monitor.start()
            logger.info("[DAEMON READY] Autonomous multi-agent rerouting engine active.")

            # Run until shutdown event or client disconnected
            while not stop_event.is_set() and client.is_connected:
                await asyncio.sleep(0.5)

            # Graceful cleanup
            logger.info("Shutting down fleet monitor and disconnecting client...")
            await monitor.stop()
            await client.disconnect()
            break

        except (asyncio.CancelledError, KeyboardInterrupt):
            logger.info("Received termination signal, shutting down MAS daemon...")
            stop_event.set()
            try:
                await client.disconnect()
            except Exception:
                pass
            break
        except Exception as exc:
            logger.warning(
                "MCP connection error: %s. Retrying in %.1fs (exponential backoff)...",
                exc,
                backoff,
            )
            try:
                await client.disconnect()
            except Exception:
                pass

            try:
                await asyncio.wait_for(stop_event.wait(), timeout=backoff)
            except asyncio.TimeoutError:
                pass

            backoff = min(backoff * 1.5, max_backoff)

    logger.info("ASTRA MAS Daemon stopped gracefully.")


def main() -> None:
    """CLI entrypoint setting up OS signal handlers and starting the async event loop."""
    shutdown_event = asyncio.Event()
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)

    def _signal_handler(*_: Any) -> None:
        logger.info("Interruption signal captured. Requesting graceful shutdown...")
        shutdown_event.set()

    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, _signal_handler)
        except (NotImplementedError, RuntimeError):
            # Fallback for Windows or non-main threads
            signal.signal(sig, lambda *args: _signal_handler(*args))

    try:
        loop.run_until_complete(run_daemon(shutdown_event=shutdown_event))
    except (KeyboardInterrupt, SystemExit):
        logger.info("Exiting ASTRA MAS daemon.")
    finally:
        loop.close()


if __name__ == "__main__":
    main()
