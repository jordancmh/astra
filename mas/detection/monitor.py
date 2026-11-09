"""
ASTRA Multi-Agent System (MAS) - Active Fleet Anomaly Monitor.
Monitors vehicle telemetry and active disruptions via the Spring Boot MCP broker,
evaluates spatial intersections, and transitions affected vehicle agents to 'INTERSECTED'.
"""
import asyncio
import logging
from collections.abc import Awaitable, Callable
from typing import Any

from mas.detection.spatial import check_trajectory_anomaly_intersection
from mas.mcp_client import AstraMcpClient
from mas.models.anomaly import Anomaly
from mas.models.coordinates import Coordinates
from mas.models.vehicle_agent import VehicleAgent

logger = logging.getLogger(__name__)

CollisionCallback = Callable[[VehicleAgent, Anomaly], Awaitable[None]]


class FleetMonitor:
    """
    Asynchronous background monitor coordinating fleet state polling via MCP
    and lightweight spatial intersection detection (FR 3.1, FR 3.3).
    """

    def __init__(
        self,
        mcp_client: AstraMcpClient,
        poll_interval_seconds: float = 1.0,
        on_collision: CollisionCallback | None = None,
    ) -> None:
        self.mcp_client = mcp_client
        self.poll_interval_seconds = poll_interval_seconds
        self.on_collision = on_collision

        self.agents: dict[str, VehicleAgent] = {}
        self.active_anomalies: dict[str, Anomaly] = {}

        self._polling_task: asyncio.Task[None] | None = None
        self._is_running = False

    @property
    def is_running(self) -> bool:
        """Returns True if the background monitoring loop is actively running."""
        return self._is_running and self._polling_task is not None and not self._polling_task.done()

    def register_agent(self, agent: VehicleAgent) -> None:
        """Registers or updates a VehicleAgent in the active fleet registry."""
        self.agents[agent.vehicle_id] = agent
        logger.debug("Registered vehicle agent %s", agent.vehicle_id)

    def get_agent(self, vehicle_id: str) -> VehicleAgent | None:
        """Retrieves a VehicleAgent by its alphanumeric identifier."""
        return self.agents.get(vehicle_id)

    def register_anomaly(self, anomaly: Anomaly) -> None:
        """Registers or updates an active anomaly."""
        self.active_anomalies[anomaly.anomaly_id] = anomaly
        logger.info("Registered anomaly %s (%s)", anomaly.anomaly_id, anomaly.anomaly_type.value)

    def remove_anomaly(self, anomaly_id: str) -> None:
        """Removes an anomaly and normalizes any agent flagging it."""
        if anomaly_id in self.active_anomalies:
            del self.active_anomalies[anomaly_id]
            for agent in self.agents.values():
                if agent.intersected_anomaly_id == anomaly_id:
                    agent.clear_intersection()
            logger.info("Removed anomaly %s", anomaly_id)

    def clear_anomalies(self) -> None:
        """Clears all active anomalies and resets intersected vehicle states."""
        self.active_anomalies.clear()
        for agent in self.agents.values():
            if agent.status == "INTERSECTED":
                agent.clear_intersection()
        logger.info("Cleared all active anomalies from monitor")

    async def poll_once(self) -> list[tuple[str, str]]:
        """
        Executes a single monitoring cycle:
        1. Queries current fleet state via AstraMcpClient.get_fleet_state().
        2. Syncs vehicle positions from backend response payload.
        3. Parses any active disruptions included in the state payload.
        4. Runs spatial intersection analysis for all agents with active trajectories.
        5. Flags affected agents with 'INTERSECTED' and triggers on_collision callback.

        Returns:
            List of detected collision pairs: [(vehicle_id, anomaly_id), ...]
        """
        try:
            fleet_data = await self.mcp_client.get_fleet_state()
        except Exception as exc:
            logger.warning("Failed to fetch fleet state during poll cycle: %s", exc)
            return []

        # 1. Sync fleet vehicle positions if returned
        vehicles_data = fleet_data.get("vehicles", [])
        if isinstance(vehicles_data, list):
            for v_data in vehicles_data:
                if isinstance(v_data, dict):
                    v_id = v_data.get("vehicleId") or v_data.get("id")
                    if v_id and v_id in self.agents:
                        agent = self.agents[v_id]
                        if "longitude" in v_data and "latitude" in v_data:
                            agent.current_location = Coordinates(
                                longitude=v_data["longitude"],
                                latitude=v_data["latitude"],
                            )
                        if "speedKmh" in v_data and v_data["speedKmh"] is not None:
                            agent.speed_kmh = float(v_data["speedKmh"])
                        if "bearing" in v_data and v_data["bearing"] is not None:
                            agent.bearing = float(v_data["bearing"])
                        if "routeId" in v_data and v_data["routeId"] is not None:
                            agent.route_id = str(v_data["routeId"])

        # 2. Sync active anomalies if included in payload
        anomalies_data = fleet_data.get("anomalies", []) or fleet_data.get("activeAnomalies", [])
        if isinstance(anomalies_data, list):
            for a_data in anomalies_data:
                if isinstance(a_data, dict):
                    try:
                        anomaly = Anomaly.model_validate(a_data)
                        self.register_anomaly(anomaly)
                    except Exception as exc:
                        logger.debug("Could not parse anomaly payload: %s", exc)

        # 3. Perform spatial collision detection
        detected_collisions: list[tuple[str, str]] = []

        for agent in self.agents.values():
            trajectory = agent.upcoming_trajectory
            if not trajectory:
                # If no upcoming trajectory defined, check current location against anomalies
                trajectory = [agent.current_location]

            for anomaly in self.active_anomalies.values():
                if not anomaly.is_active:
                    continue

                is_intersecting = check_trajectory_anomaly_intersection(
                    trajectory=trajectory,
                    anomaly=anomaly,
                )

                if is_intersecting:
                    collision_pair = (agent.vehicle_id, anomaly.anomaly_id)
                    detected_collisions.append(collision_pair)

                    # Trigger state transition if not already flagged for this anomaly
                    if agent.status != "INTERSECTED" or agent.intersected_anomaly_id != anomaly.anomaly_id:
                        agent.set_intersected(anomaly.anomaly_id)
                        logger.warning(
                            "Collision detected! VehicleAgent %s intersected with Anomaly %s (%s)",
                            agent.vehicle_id,
                            anomaly.anomaly_id,
                            anomaly.anomaly_type.value,
                        )

                        if self.on_collision:
                            try:
                                await self.on_collision(agent, anomaly)
                            except Exception as cb_exc:
                                logger.error("Error in on_collision callback: %s", cb_exc)

                    # First hit is recorded; break to next agent
                    break

        return detected_collisions

    async def _run_loop(self) -> None:
        """Internal background polling loop."""
        logger.info("FleetMonitor background task started (interval: %.1fs)", self.poll_interval_seconds)
        while self._is_running:
            try:
                await self.poll_once()
            except asyncio.CancelledError:
                break
            except Exception as exc:
                logger.warning("Unexpected error during fleet monitor poll: %s", exc)

            try:
                await asyncio.sleep(self.poll_interval_seconds)
            except asyncio.CancelledError:
                break
        logger.info("FleetMonitor background task stopped")

    def start(self) -> None:
        """Starts the active background monitoring loop."""
        if self._is_running:
            return

        self._is_running = True
        self._polling_task = asyncio.create_task(self._run_loop())

    async def stop(self) -> None:
        """Cancels and gracefully shuts down the background monitoring loop."""
        if not self._is_running:
            return

        self._is_running = False
        if self._polling_task:
            self._polling_task.cancel()
            try:
                await self._polling_task
            except asyncio.CancelledError:
                pass
            finally:
                self._polling_task = None

    async def __aenter__(self) -> "FleetMonitor":
        self.start()
        return self

    async def __aexit__(self, exc_type: Any, exc_val: Any, exc_tb: Any) -> None:
        await self.stop()
