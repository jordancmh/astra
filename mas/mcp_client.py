"""
ASTRA Multi-Agent System (MAS) - Model Context Protocol (MCP) Client.
Connects to the Spring Boot MCP Broker over HTTP/SSE transport to securely
read fleet telemetry and commit negotiated routes (FR 3.3, NFR 2.1).
"""
import json
import logging
import os
from contextlib import AsyncExitStack
from typing import Any

from dotenv import load_dotenv
from mcp import types
from mcp.client.session import ClientSession
from mcp.client.sse import sse_client

from mas.models.coordinates import Coordinates

load_dotenv()

logger = logging.getLogger(__name__)


class AstraMcpClient:
    """
    Asynchronous MCP client wrapper for the ASTRA Spring Boot MCP broker.
    Provides connection lifecycle management and typed tool execution.
    """

    DEFAULT_URL = "http://localhost:8080/sse"

    def __init__(
        self,
        server_url: str | None = None,
        timeout: float = 10.0,
    ) -> None:
        self.server_url = (
            server_url or os.getenv("MCP_SERVER_URL", self.DEFAULT_URL)
        )
        self.timeout = timeout
        self._session: ClientSession | None = None
        self._exit_stack: AsyncExitStack | None = None

    @property
    def is_connected(self) -> bool:
        """Returns True if the client session is active."""
        return self._session is not None

    async def connect(self) -> ClientSession:
        """
        Establishes an SSE transport connection and initializes the MCP session.
        """
        if self._session is not None:
            return self._session

        logger.info("Connecting to ASTRA MCP Server at %s", self.server_url)
        self._exit_stack = AsyncExitStack()
        try:
            read_stream, write_stream = await self._exit_stack.enter_async_context(
                sse_client(self.server_url, timeout=self.timeout)
            )
            session = await self._exit_stack.enter_async_context(
                ClientSession(read_stream, write_stream)
            )
            await session.initialize()
            self._session = session
            logger.info("Connected and initialized MCP session with %s", self.server_url)
            return session
        except Exception as exc:
            logger.error("Failed to connect to MCP server: %s", exc)
            await self.disconnect()
            raise

    async def disconnect(self) -> None:
        """
        Closes the MCP session and gracefully shuts down the SSE connection.
        """
        if self._exit_stack:
            try:
                await self._exit_stack.aclose()
            except Exception as exc:
                logger.debug("Error during exit stack cleanup: %s", exc)
            finally:
                self._exit_stack = None
                self._session = None
                logger.info("Disconnected from ASTRA MCP Server")
        else:
            self._session = None

    async def __aenter__(self) -> "AstraMcpClient":
        await self.connect()
        return self

    async def __aexit__(self, exc_type: Any, exc_val: Any, exc_tb: Any) -> None:
        await self.disconnect()

    def _ensure_connected(self) -> ClientSession:
        if self._session is None:
            raise RuntimeError(
                "MCP client is not connected. Use 'async with client:' or call 'await client.connect()' first."
            )
        return self._session

    async def list_tools(self) -> list[types.Tool]:
        """
        Queries the MCP server for available tools.
        """
        session = self._ensure_connected()
        result = await session.list_tools()
        return result.tools

    async def call_tool(
        self,
        name: str,
        arguments: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """
        Invokes an MCP tool on the server and parses the JSON response payload.
        """
        session = self._ensure_connected()
        response = await session.call_tool(name, arguments=arguments or {})

        if getattr(response, "is_error", False):
            error_msg = "Tool execution failed"
            if response.content and hasattr(response.content[0], "text"):
                error_msg = response.content[0].text
            raise RuntimeError(f"MCP Tool '{name}' returned error: {error_msg}")

        # Extract text content from tool result
        if response.content:
            first_content = response.content[0]
            if hasattr(first_content, "text"):
                try:
                    parsed = json.loads(first_content.text)
                    if isinstance(parsed, dict):
                        return parsed
                    return {"result": parsed}
                except json.JSONDecodeError:
                    return {"raw": first_content.text}

        return {}

    async def get_fleet_state(
        self,
        vehicle_id: str | None = None,
    ) -> dict[str, Any]:
        """
        Fetches current vehicle telemetry and status from PostgreSQL via the Spring Boot MCP broker.
        """
        args: dict[str, Any] = {}
        if vehicle_id:
            args["vehicleId"] = vehicle_id.strip()
        return await self.call_tool("getFleetState", arguments=args)

    async def update_route(
        self,
        vehicle_id: str,
        route_id: str,
        coordinates: list[list[float]],
        rationale: str | None = None,
    ) -> dict[str, Any]:
        """
        Commits a negotiated detour geometry to PostgreSQL via the Spring Boot MCP broker.
        Coordinates must be in [longitude, latitude] order within the Klang Valley bounding box.
        """
        if not vehicle_id or not vehicle_id.strip():
            raise ValueError("vehicle_id cannot be empty")
        if not route_id or not route_id.strip():
            raise ValueError("route_id cannot be empty")
        if len(coordinates) < 2:
            raise ValueError("coordinates must contain at least 2 points")

        # Guardrail: Validate coordinates [longitude, latitude] and Klang Valley bounds
        for pt in coordinates:
            Coordinates.model_validate(pt)

        args: dict[str, Any] = {
            "vehicleId": vehicle_id.strip(),
            "routeId": route_id.strip(),
            "coordinates": coordinates,
        }
        if rationale:
            args["rationale"] = rationale.strip()
        return await self.call_tool("updateRoute", arguments=args)
