import pytest
from pydantic import ValidationError
from mas.models.coordinates import Coordinates


def test_coordinates_valid_dict():
    coords = Coordinates(longitude=101.55, latitude=3.12)
    assert coords.longitude == 101.55
    assert coords.latitude == 3.12
    assert coords.to_coords() == [101.55, 3.12]


def test_coordinates_valid_tuple_or_list():
    coords_from_list = Coordinates.model_validate([101.65, 3.05])
    assert coords_from_list.longitude == 101.65
    assert coords_from_list.latitude == 3.05

    coords_from_tuple = Coordinates.model_validate((101.70, 3.20))
    assert coords_from_tuple.longitude == 101.70
    assert coords_from_tuple.latitude == 3.20


def test_coordinates_boundary_edges():
    # Min boundary
    c_min = Coordinates(longitude=101.40, latitude=2.90)
    assert c_min.to_coords() == [101.40, 2.90]

    # Max boundary
    c_max = Coordinates(longitude=101.90, latitude=3.30)
    assert c_max.to_coords() == [101.90, 3.30]


def test_coordinates_out_of_bounds_longitude():
    with pytest.raises(ValidationError) as exc_info:
        Coordinates(longitude=101.39, latitude=3.10)
    assert "longitude" in str(exc_info.value)

    with pytest.raises(ValidationError) as exc_info:
        Coordinates(longitude=101.91, latitude=3.10)
    assert "longitude" in str(exc_info.value)


def test_coordinates_out_of_bounds_latitude():
    with pytest.raises(ValidationError) as exc_info:
        Coordinates(longitude=101.50, latitude=2.89)
    assert "latitude" in str(exc_info.value)

    with pytest.raises(ValidationError) as exc_info:
        Coordinates(longitude=101.50, latitude=3.31)
    assert "latitude" in str(exc_info.value)


def test_coordinates_rejects_inverted_order():
    # If caller erroneously passes [latitude, longitude], longitude=3.12 fails Klang Valley bounds (101.40 - 101.90)
    with pytest.raises(ValidationError) as exc_info:
        Coordinates.model_validate([3.12, 101.55])
    assert "longitude" in str(exc_info.value)


def test_coordinates_invalid_list_length():
    with pytest.raises(ValidationError) as exc_info:
        Coordinates.model_validate([101.55])
    assert "Coordinates list or tuple must contain exactly 2 elements" in str(exc_info.value)
