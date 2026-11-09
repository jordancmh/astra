"""
ASTRA Multi-Agent System (MAS) - Anomaly Detection Module.
Provides lightweight spatial intersection algorithms and active fleet monitoring.
"""
from .spatial import (
    haversine_distance,
    point_to_segment_distance,
    point_in_polygon,
    segments_intersect,
    trajectory_intersects_radius,
    trajectory_intersects_polygon,
    check_trajectory_anomaly_intersection,
)
from .monitor import FleetMonitor

__all__ = [
    "haversine_distance",
    "point_to_segment_distance",
    "point_in_polygon",
    "segments_intersect",
    "trajectory_intersects_radius",
    "trajectory_intersects_polygon",
    "check_trajectory_anomaly_intersection",
    "FleetMonitor",
]
