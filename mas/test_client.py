"""
Standalone connectivity test script for the ASTRA Python MCP Client (Issue #14).
Connects to the Spring Boot MCP Server, asserts tool registration,
and queries live fleet state.

Usage:
    PYTHONPATH=. mas/venv/bin/python mas/test_client.py
"""
import asyncio
import logging
import os
import sys

import httpx

from mas.mcp_client import AstraMcpClient


try:
    import httpx2
    Httpx2ConnectError = (httpx2.ConnectError, httpx2.NetworkError)
except ImportError:
    Httpx2ConnectError = ()


async def main() -> int:
    server_url = os.getenv("MCP_SERVER_URL", "http://localhost:8080/sse")
    print("=" * 60)
    print(" ASTRA MAS - Python MCP Client Connectivity Test")
    print(f" Target Server URL: {server_url}")
    print("=" * 60)

    client = AstraMcpClient(server_url=server_url, timeout=5.0)
    try:
        print("\n[1/3] Connecting to Spring Boot MCP Server via SSE...")
        await client.connect()
        print("      Connected successfully!")

        print("\n[2/3] Querying registered MCP tools...")
        tools = await client.list_tools()
        tool_names = [t.name for t in tools]
        print(f"      Discovered {len(tools)} tools: {tool_names}")

        assert "getFleetState" in tool_names, (
            f"Missing 'getFleetState' tool! Discovered: {tool_names}"
        )
        assert "updateRoute" in tool_names, (
            f"Missing 'updateRoute' tool! Discovered: {tool_names}"
        )
        print("      Assertion passed: 'getFleetState' and 'updateRoute' exist.")

        print("\n[3/3] Executing 'getFleetState' tool...")
        fleet_data = await client.get_fleet_state()
        print(f"      Response: {fleet_data}")
        print("\n" + "=" * 60)
        print(" SUCCESS: All MCP client connectivity checks passed!")
        print("=" * 60)
        return 0

    except (ConnectionRefusedError, httpx.ConnectError, *Httpx2ConnectError) as exc:
        print(f"\n[ERROR] Connection failed at {server_url}: {exc}")
        print("        Ensure the Spring Boot backend is running:")
        print("        cd backend && ./mvnw spring-boot:run")
        print("        Or specify MCP_SERVER_URL if running on an alternate port/path.")
        return 1
    except AssertionError as exc:
        print(f"\n[ERROR] Tool verification failed: {exc}")
        return 1
    except Exception as exc:
        print(f"\n[ERROR] MCP Client test encountered an error: {type(exc).__name__}: {exc}")
        return 1
    finally:
        await client.disconnect()


if __name__ == "__main__":
    logging.basicConfig(level=logging.WARNING)
    sys.exit(asyncio.run(main()))
