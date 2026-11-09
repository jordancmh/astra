"""
Unit tests for the pure-Python lightweight spatial intersection engine (Issue #15, Task 4.1).
Validates Haversine distance in meters, ray-casting point-in-polygon, segment intersections,
and the 4-stage intersection pipeline within Klang Valley bounds.
"""
from mas.detection.spatial import (
    check_trajectory_anomaly_intersection,
    haversine_distance,
    point_in_polygon,
    point_to_segment_distance,
    segments_intersect,
    trajectory_intersects_polygon,
    trajectory_intersects_radius,
)
from mas.models.anomaly import Anomaly, AnomalyType
from mas.models.coordinates import Coordinates


def test_haversine_distance_known_points():
    """Verifies Haversine formula strictly calculates distance in meters between known points."""
    # Merdeka 118 [lng, lat]
    p1 = (101.7005, 3.1415)
    # Petronas Twin Towers [lng, lat]
    p2 = (101.7116, 3.1579)

    distance = haversine_distance(p1, p2)
    # Distance is ~2.2 km
    assert 2100.0 < distance < 2300.0
    # Reflexivity: distance to self is 0
    assert haversine_distance(p1, p1) == 0.0


def test_point_to_segment_distance():
    """Verifies perpendicular and endpoint distance calculations in meters."""
    seg_start = (101.6000, 3.1000)
    seg_end = (101.6200, 3.1000)  # Horizontal segment along latitude 3.1000

    # Point directly on the segment midpoint
    pt_on = (101.6100, 3.1000)
    assert point_to_segment_distance(pt_on, seg_start, seg_end) < 1.0

    # Point perpendicular to midpoint (~0.001 deg lat is ~111 meters)
    pt_perp = (101.6100, 3.1010)
    dist_perp = point_to_segment_distance(pt_perp, seg_start, seg_end)
    assert 105.0 < dist_perp < 120.0

    # Point beyond seg_end
    pt_beyond = (101.6300, 3.1000)
    dist_beyond = point_to_segment_distance(pt_beyond, seg_start, seg_end)
    dist_to_end = haversine_distance(pt_beyond, seg_end)
    assert abs(dist_beyond - dist_to_end) < 2.0


def test_point_in_polygon_inside_and_outside():
    """Verifies 2D ray casting for point containment."""
    # Closed square polygon in Petaling Jaya
    poly = [
        (101.6000, 3.1000),
        (101.6200, 3.1000),
        (101.6200, 3.1200),
        (101.6000, 3.1200),
        (101.6000, 3.1000),
    ]

    # Point inside
    assert point_in_polygon((101.6100, 3.1100), poly) is True

    # Point outside
    assert point_in_polygon((101.6300, 3.1100), poly) is False
    assert point_in_polygon((101.5900, 3.0900), poly) is False


def test_segments_intersect():
    """Verifies 2D segment intersection detection."""
    # Crossing segments (X shape)
    s1_a, s1_b = (101.6000, 3.1000), (101.6200, 3.1200)
    s2_a, s2_b = (101.6000, 3.1200), (101.6200, 3.1000)
    assert segments_intersect(s1_a, s1_b, s2_a, s2_b) is True

    # Parallel disjoint segments
    s3_a, s3_b = (101.6000, 3.1300), (101.6200, 3.1300)
    assert segments_intersect(s1_a, s1_b, s3_a, s3_b) is False


def test_trajectory_radius_direct_hit():
    """Verifies trajectory piercing the disruption radius returns True."""
    center = (101.6100, 3.1100)
    radius_meters = 500.0

    # Trajectory cutting directly through the center
    trajectory = [
        (101.6000, 3.1100),
        (101.6200, 3.1100),
    ]
    assert trajectory_intersects_radius(trajectory, center, radius_meters) is True


def test_trajectory_radius_near_miss():
    """Verifies trajectory passing outside radius returns False."""
    center = (101.6100, 3.1100)
    radius_meters = 200.0  # 200m radius

    # Trajectory passing ~0.005 deg north (~555 meters away)
    trajectory = [
        (101.6000, 3.1150),
        (101.6200, 3.1150),
    ]
    assert trajectory_intersects_radius(trajectory, center, radius_meters) is False


def test_trajectory_polygon_piercing():
    """Verifies trajectory that passes through a polygon is detected."""
    poly = [
        (101.6100, 3.1100),
        (101.6200, 3.1100),
        (101.6200, 3.1200),
        (101.6100, 3.1200),
    ]
    # Piercing segment from south-west to north-east
    trajectory = [
        (101.6050, 3.1050),
        (101.6250, 3.1250),
    ]
    assert trajectory_intersects_polygon(trajectory, poly) is True


def test_trajectory_polygon_inside_start():
    """Verifies trajectory starting inside a polygon is detected."""
    poly = [
        (101.6100, 3.1100),
        (101.6200, 3.1100),
        (101.6200, 3.1200),
        (101.6100, 3.1200),
    ]
    trajectory = [
        (101.6150, 3.1150),  # Inside
        (101.6300, 3.1300),  # Outside
    ]
    assert trajectory_intersects_polygon(trajectory, poly) is True


def test_trajectory_polygon_parallel_miss():
    """Verifies parallel trajectory outside polygon returns False."""
    poly = [
        (101.6100, 3.1100),
        (101.6200, 3.1100),
        (101.6200, 3.1200),
        (101.6100, 3.1200),
    ]
    trajectory = [
        (101.6300, 3.1000),
        (101.6300, 3.1300),
    ]
    assert trajectory_intersects_polygon(trajectory, poly) is False


def test_check_trajectory_anomaly_pipeline_aabb_rejection():
    """Verifies that distant trajectories are immediately rejected by AABB pre-filter."""
    anomaly = Anomaly(
        anomaly_id="D-KL-001",
        anomaly_type=AnomalyType.ROAD_CLOSURE,
        center=Coordinates(longitude=101.4500, latitude=3.0000),  # Klang / Port Klang
        radius_meters=300.0,
    )
    # Trajectory in Kuala Lumpur city center (~25 km away)
    trajectory = [
        Coordinates(longitude=101.7000, latitude=3.1500),
        Coordinates(longitude=101.7100, latitude=3.1600),
    ]
    assert check_trajectory_anomaly_intersection(trajectory, anomaly) is False


def test_check_trajectory_anomaly_pipeline_hit():
    """Verifies that an intersecting trajectory passes through all stages and returns True."""
    anomaly = Anomaly(
        anomaly_id="D-PJ-001",
        anomaly_type=AnomalyType.ROAD_CLOSURE,
        center=Coordinates(longitude=101.6100, latitude=3.1100),
        radius_meters=500.0,
        polygon_coordinates=[
            Coordinates(longitude=101.6050, latitude=3.1050),
            Coordinates(longitude=101.6150, latitude=3.1050),
            Coordinates(longitude=101.6150, latitude=3.1150),
            Coordinates(longitude=101.6050, latitude=3.1150),
        ],
    )
    # Trajectory entering the disruption zone
    trajectory = [
        Coordinates(longitude=101.5900, latitude=3.1100),
        Coordinates(longitude=101.6300, latitude=3.1100),
    ]
    assert check_trajectory_anomaly_intersection(trajectory, anomaly) is True


def test_check_trajectory_anomaly_inactive():
    """Verifies that inactive disruptions are ignored."""
    anomaly = Anomaly(
        anomaly_id="D-PJ-002",
        anomaly_type=AnomalyType.CONGESTION,
        center=Coordinates(longitude=101.6100, latitude=3.1100),
        radius_meters=500.0,
        is_active=False,
    )
    trajectory = [
        Coordinates(longitude=101.6000, latitude=3.1100),
        Coordinates(longitude=101.6200, latitude=3.1100),
    ]
    assert check_trajectory_anomaly_intersection(trajectory, anomaly) is False
