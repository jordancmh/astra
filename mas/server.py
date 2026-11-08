"""
ASTRA Multi-Agent System (MAS) FastMCP Server.
Communicates via standard input/output (stdio) transport for Spring Boot MCP broker subprocess execution.
"""
import os
import sys
from dotenv import load_dotenv
from fastmcp import FastMCP
from mas.models.coordinates import Coordinates
from mas.models.vehicle_agent import VehicleAgent

load_dotenv()

# Instantiate FastMCP server
mcp = FastMCP("ASTRA MAS Engine")


@mcp.tool()
def ping() -> dict:
    """
    Health check tool verifying ASTRA MAS Engine responsiveness.
    """
    return {
        "status": "UP",
        "service": "ASTRA MAS Engine",
        "transport": "stdio"
    }


@mcp.tool()
def get_agent_status(vehicle_id: str, cargo_priority: int, current_coords: list[float]) -> dict:
    """
    Validates vehicle parameters against Klang Valley spatial constraints and cargo priority bounds,
    returning initial agent operational status.
    """
    agent = VehicleAgent(
        vehicle_id=vehicle_id,
        cargo_priority=cargo_priority,
        current_location=current_coords
    )
    return {
        "vehicle_id": agent.vehicle_id,
        "cargo_priority": agent.cargo_priority,
        "current_coords": agent.current_location.to_coords(),
        "status": "READY"
    }


if __name__ == "__main__":
    mcp.run(transport="stdio")
