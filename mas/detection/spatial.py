"""
ASTRA Multi-Agent System (MAS) - Pure-Python Lightweight Spatial Engine.
Performs geometric intersection tests between vehicle trajectories and active disruptions.
Strictly requires [longitude, latitude] coordinate ordering (SRID 4326).
"""
import math
from typing import Sequence

from mas.models.anomaly import Anomaly
from mas.models.coordinates import Coordinates

EARTH_RADIUS_METERS = 6_371_000.0


def haversine_distance(
    coord1: tuple[float, float] | Coordinates,
    coord2: tuple[float, float] | Coordinates,
) -> float:
    """
    Calculates great-circle distance in meters between two [longitude, latitude] coordinates.
    Uses Earth mean radius R = 6,371,000 meters.
    """
    lng1 = coord1.longitude if isinstance(coord1, Coordinates) else coord1[0]
    lat1 = coord1.latitude if isinstance(coord1, Coordinates) else coord1[1]
    lng2 = coord2.longitude if isinstance(coord2, Coordinates) else coord2[0]
    lat2 = coord2.latitude if isinstance(coord2, Coordinates) else coord2[1]

    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    lat1_rad = math.radians(lat1)
    lat2_rad = math.radians(lat2)

    a = (
        math.sin(dlat / 2.0) ** 2
        + math.cos(lat1_rad) * math.cos(lat2_rad) * math.sin(dlng / 2.0) ** 2
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(max(0.0, 1.0 - a)))
    return EARTH_RADIUS_METERS * c


def point_to_segment_distance(
    pt: tuple[float, float] | Coordinates,
    seg_start: tuple[float, float] | Coordinates,
    seg_end: tuple[float, float] | Coordinates,
) -> float:
    """
    Calculates the minimum distance in meters from point `pt` to line segment `seg_start -> seg_end`.
    Projects coordinates to a local Cartesian plane centered at the segment for high performance.
    """
    px = pt.longitude if isinstance(pt, Coordinates) else pt[0]
    py = pt.latitude if isinstance(pt, Coordinates) else pt[1]
    ax = seg_start.longitude if isinstance(seg_start, Coordinates) else seg_start[0]
    ay = seg_start.latitude if isinstance(seg_start, Coordinates) else seg_start[1]
    bx = seg_end.longitude if isinstance(seg_end, Coordinates) else seg_end[0]
    by = seg_end.latitude if isinstance(seg_end, Coordinates) else seg_end[1]

    # If segment endpoints are identical, return distance to the point
    if ax == bx and ay == by:
        return haversine_distance((px, py), (ax, ay))

    # Metric scale factors around mean latitude
    mid_lat_rad = math.radians((ay + by + py) / 3.0)
    meters_per_deg_lat = 111_139.0
    meters_per_deg_lng = 111_139.0 * math.cos(mid_lat_rad)

    # Local Cartesian conversion (origin at A)
    ax_m, ay_m = 0.0, 0.0
    bx_m = (bx - ax) * meters_per_deg_lng
    by_m = (by - ay) * meters_per_deg_lat
    px_m = (px - ax) * meters_per_deg_lng
    py_m = (py - ay) * meters_per_deg_lat

    vx = bx_m - ax_m
    vy = by_m - ay_m
    wx = px_m - ax_m
    wy = py_m - ay_m

    c1 = wx * vx + wy * vy
    if c1 <= 0.0:
        # Closest point is A
        return math.hypot(px_m - ax_m, py_m - ay_m)

    c2 = vx * vx + vy * vy
    if c2 <= c1:
        # Closest point is B
        return math.hypot(px_m - bx_m, py_m - by_m)

    # Closest point is on the segment
    t = c1 / c2
    proj_x = ax_m + t * vx
    proj_y = ay_m + t * vy
    return math.hypot(px_m - proj_x, py_m - proj_y)


def point_in_polygon(
    pt: tuple[float, float] | Coordinates,
    polygon: Sequence[tuple[float, float] | Coordinates],
) -> bool:
    """
    Determines whether a point (lng, lat) is inside a 2D closed polygon using ray casting.
    Polygon coordinates must be in [longitude, latitude] order.
    """
    if len(polygon) < 3:
        return False

    px = pt.longitude if isinstance(pt, Coordinates) else pt[0]
    py = pt.latitude if isinstance(pt, Coordinates) else pt[1]

    coords = [
        (c.longitude, c.latitude) if isinstance(c, Coordinates) else (c[0], c[1])
        for c in polygon
    ]

    inside = False
    n = len(coords)
    j = n - 1

    for i in range(n):
        xi, yi = coords[i]
        xj, yj = coords[j]

        # Check if ray from (px, py) intersects segment (xi, yi)-(xj, yj)
        if ((yi > py) != (yj > py)) and (
            px < (xj - xi) * (py - yi) / (yj - yi) + xi
        ):
            inside = not inside
        j = i

    return inside


def _ccw(
    a: tuple[float, float],
    b: tuple[float, float],
    c: tuple[float, float],
) -> bool:
    return (c[1] - a[1]) * (b[0] - a[0]) > (b[1] - a[1]) * (c[0] - a[0])


def segments_intersect(
    p1: tuple[float, float] | Coordinates,
    p2: tuple[float, float] | Coordinates,
    q1: tuple[float, float] | Coordinates,
    q2: tuple[float, float] | Coordinates,
) -> bool:
    """
    Determines whether line segment p1-p2 intersects line segment q1-q2 in 2D (lng, lat).
    """
    p1_t = (p1.longitude, p1.latitude) if isinstance(p1, Coordinates) else (p1[0], p1[1])
    p2_t = (p2.longitude, p2.latitude) if isinstance(p2, Coordinates) else (p2[0], p2[1])
    q1_t = (q1.longitude, q1.latitude) if isinstance(q1, Coordinates) else (q1[0], q1[1])
    q2_t = (q2.longitude, q2.latitude) if isinstance(q2, Coordinates) else (q2[0], q2[1])

    return (
        _ccw(p1_t, q1_t, q2_t) != _ccw(p2_t, q1_t, q2_t)
        and _ccw(p1_t, p2_t, q1_t) != _ccw(p1_t, p2_t, q2_t)
    )


def trajectory_intersects_radius(
    trajectory: Sequence[tuple[float, float] | Coordinates],
    center: tuple[float, float] | Coordinates,
    radius_meters: float,
) -> bool:
    """
    Tests if any segment or vertex of the trajectory passes within radius_meters of center.
    """
    if not trajectory:
        return False

    if len(trajectory) == 1:
        return haversine_distance(trajectory[0], center) <= radius_meters

    for i in range(len(trajectory) - 1):
        dist = point_to_segment_distance(center, trajectory[i], trajectory[i + 1])
        if dist <= radius_meters:
            return True

    return False


def trajectory_intersects_polygon(
    trajectory: Sequence[tuple[float, float] | Coordinates],
    polygon: Sequence[tuple[float, float] | Coordinates],
) -> bool:
    """
    Tests if a trajectory intersects a polygon:
    1. Any trajectory vertex is strictly inside the polygon.
    2. Any trajectory segment intersects any polygon boundary edge.
    """
    if not trajectory or len(polygon) < 3:
        return False

    # Check vertex containment
    for pt in trajectory:
        if point_in_polygon(pt, polygon):
            return True

    # Check segment-edge crossings
    poly_n = len(polygon)
    for i in range(len(trajectory) - 1):
        t_start = trajectory[i]
        t_end = trajectory[i + 1]
        for j in range(poly_n):
            poly_start = polygon[j]
            poly_end = polygon[(j + 1) % poly_n]
            if segments_intersect(t_start, t_end, poly_start, poly_end):
                return True

    return False


def check_trajectory_anomaly_intersection(
    trajectory: Sequence[Coordinates],
    anomaly: Anomaly,
) -> bool:
    """
    4-stage spatial intersection pipeline:
    1. Fast bounding-box (AABB) rejection filter.
    2. Single-point trajectory evaluation (if len == 1).
    3. Polygon containment / edge piercing (if polygon_coordinates present).
    4. Haversine circle distance check (if radius_meters > 0).
    """
    if not trajectory or not anomaly.is_active:
        return False

    # Stage 1: Fast AABB Pre-filter
    t_min_lng = min(pt.longitude for pt in trajectory)
    t_max_lng = max(pt.longitude for pt in trajectory)
    t_min_lat = min(pt.latitude for pt in trajectory)
    t_max_lat = max(pt.latitude for pt in trajectory)

    a_min_lng, a_min_lat, a_max_lng, a_max_lat = anomaly.bounding_box()

    # Disjoint bounding boxes check
    if (
        t_max_lng < a_min_lng
        or t_min_lng > a_max_lng
        or t_max_lat < a_min_lat
        or t_min_lat > a_max_lat
    ):
        return False

    # Stage 2: Polygon check (if defined)
    if len(anomaly.polygon_coordinates) >= 3:
        if trajectory_intersects_polygon(trajectory, anomaly.polygon_coordinates):
            return True

    # Stage 3: Radius check (if defined and positive)
    if anomaly.radius_meters > 0.0:
        if trajectory_intersects_radius(trajectory, anomaly.center, anomaly.radius_meters):
            return True

    return False
