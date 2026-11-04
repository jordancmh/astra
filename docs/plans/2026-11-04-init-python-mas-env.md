# Initialize Python MAS Environment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Initialize the `/mas` directory with a Python 3.12+ virtual environment, Pydantic domain models (`Coordinates` and `VehicleAgent`) enforcing strict Klang Valley `[longitude, latitude]` spatial ordering, a FastMCP server running over stdio transport ready for Spring Boot MCP broker integration, and appropriate `.gitignore` protection.

**Architecture:** A modular Python package (`mas/`) housing domain entities in `mas/models/`, MCP RPC tools and stdio transport lifecycle in `mas/server.py`, and comprehensive automated test suites in `mas/tests/`. Domain models use Pydantic V2 to enforce type safety, value constraints, and SRID 4326 coordinate conventions before any negotiation logic is executed.

**Tech Stack:** Python 3.12+, FastMCP (`fastmcp`), Pydantic V2 (`pydantic>=2.0`), `python-dotenv`, `pytest`, `pytest-asyncio`.

---

## Global Constraints

- **Python Version:** Python 3.12+ (local WSL environment runs Python 3.12.3).
- **Target Directory:** `/mas` at the repository root.
- **Coordinate Order:** Strictly `[longitude, latitude]` for all spatial data (SRID 4326).
- **Klang Valley Bounding Box:** Longitude in `[101.40, 101.90]`, Latitude in `[2.90, 3.30]`. Inverted coordinates `[latitude, longitude]` must fail validation immediately.
- **Cargo Priority Range:** Integer between `1` (Standard Dry Goods) and `5` (Critical Pharmaceuticals / Medical).
- **MCP Transport:** FastMCP over `stdio` transport.
- **Security Perimeter (NFR 2.1, NFR 2.2):** No direct database access or SQL drivers (`psycopg2`, `asyncpg`) in `requirements.txt`.
- **Git Hygiene:** Local virtual environment (`mas/venv/`, `mas/.venv/`), `.env` files, and bytecode cache (`__pycache__/`, `.pytest_cache/`) must be ignored by Git.
- **Test Strategy:** Strict Test-Driven Development (Red-Green-Refactor) for every component.

---

### Task 1: Git Hygiene & Python Environment Scaffolding

**Files:**
- Modify: `.gitignore`
- Create: `mas/requirements.txt`
- Create: `mas/.env.example`

**Interfaces:**
- Produces: Isolated Python virtual environment at `mas/venv`, installed dependencies (`fastmcp`, `pydantic`, `python-dotenv`, `pytest`), and Git protection against environment files and caches.

- [ ] **Step 1: Update `.gitignore` with Python MAS exclusions**

Append the following block to `.gitignore`:

```gitignore
# Python MAS & Virtual Environments
mas/venv/
mas/.venv/
venv/
.venv/
.env
.env.*
!.env.example
mas/.env
mas/.env.*
!mas/.env.example
__pycache__/
*.py[cod]
*$py.class
.pytest_cache/
.coverage
htmlcov/
```

- [ ] **Step 2: Create `mas/requirements.txt`**

Write `mas/requirements.txt`:

```text
fastmcp>=0.1.0
pydantic>=2.0.0
python-dotenv>=1.0.0
pytest>=8.0.0
pytest-asyncio>=0.23.0
```

- [ ] **Step 3: Create `mas/.env.example`**

Write `mas/.env.example`:

```text
# ASTRA Multi-Agent System (MAS) Configuration
MAS_ENVIRONMENT=development
MAS_LOG_LEVEL=INFO
```

- [ ] **Step 4: Initialize virtual environment and install dependencies**

Run:
```bash
python3 -m venv mas/venv
./mas/venv/bin/pip install --upgrade pip
./mas/venv/bin/pip install -r mas/requirements.txt
```
Expected: Virtual environment created with all dependencies successfully installed.

- [ ] **Step 5: Verify environment imports and gitignore protection**

Run:
```bash
./mas/venv/bin/python -c "import fastmcp, pydantic, dotenv, pytest; print('MAS Environment Ready: Python', pydantic.__version__)"
git status --ignored mas/venv
```
Expected: Output prints `MAS Environment Ready: Python 2.x.x` and `mas/venv` is listed under `Ignored files`.

- [ ] **Step 6: Commit Task 1 files**

Run:
```bash
git add .gitignore mas/requirements.txt mas/.env.example
git commit -m "feat(mas): scaffold python virtual environment and update gitignore"
```

---

### Task 2: Implement `Coordinates` Domain Model with Klang Valley Validation (TDD)

**Files:**
- Create: `mas/models/coordinates.py`
- Create: `mas/models/__init__.py`
- Create: `mas/tests/__init__.py`
- Create: `mas/tests/test_coordinates.py`

**Interfaces:**
- Consumes: None (base spatial primitives).
- Produces: `Coordinates` Pydantic model with fields `longitude: float` (`[101.40, 101.90]`), `latitude: float` (`[2.90, 3.30]`), tuple/list auto-parsing validator, and `.to_coords() -> list[float]` export helper.

- [ ] **Step 1: Write the failing unit tests for `Coordinates`**

Create `mas/tests/__init__.py` (empty) and write `mas/tests/test_coordinates.py`:

```python
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
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
./mas/venv/bin/pytest mas/tests/test_coordinates.py -v
```
Expected: FAIL with `ModuleNotFoundError: No module named 'mas.models.coordinates'`.

- [ ] **Step 3: Implement `Coordinates` model in `mas/models/coordinates.py`**

Create `mas/models/__init__.py` and write `mas/models/coordinates.py`:

```python
from typing import Any
from pydantic import BaseModel, Field, model_validator


class Coordinates(BaseModel):
    """
    Spatial coordinates strictly bounded to the Klang Valley region (SRID 4326).
    Enforces strict [longitude, latitude] ordering.
    """
    longitude: float = Field(
        ...,
        ge=101.40,
        le=101.90,
        description="Longitude in WGS 84 (Klang Valley bounds: 101.40 to 101.90)"
    )
    latitude: float = Field(
        ...,
        ge=2.90,
        le=3.30,
        description="Latitude in WGS 84 (Klang Valley bounds: 2.90 to 3.30)"
    )

    @model_validator(mode="before")
    @classmethod
    def parse_coordinate_array(cls, data: Any) -> Any:
        if isinstance(data, (list, tuple)):
            if len(data) != 2:
                raise ValueError("Coordinates list or tuple must contain exactly 2 elements: [longitude, latitude]")
            return {"longitude": data[0], "latitude": data[1]}
        return data

    def to_coords(self) -> list[float]:
        """Returns standard GeoJSON [longitude, latitude] coordinate array."""
        return [self.longitude, self.latitude]
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
PYTHONPATH=. ./mas/venv/bin/pytest mas/tests/test_coordinates.py -v
```
Expected: PASS (all 7 tests passing).

- [ ] **Step 5: Commit Task 2 files**

Run:
```bash
git add mas/models/__init__.py mas/models/coordinates.py mas/tests/__init__.py mas/tests/test_coordinates.py
git commit -m "feat(mas): implement Coordinates model with Klang Valley spatial validation"
```

---

### Task 3: Implement `VehicleAgent` Domain Model (TDD)

**Files:**
- Create: `mas/models/vehicle_agent.py`
- Modify: `mas/models/__init__.py`
- Create: `mas/tests/test_vehicle_agent.py`

**Interfaces:**
- Consumes: `Coordinates` from `mas.models.coordinates`.
- Produces: `VehicleAgent` Pydantic model with `vehicle_id: str`, `cargo_priority: int` (`1`–`5`), and `current_location: Coordinates`.

- [ ] **Step 1: Write the failing unit tests for `VehicleAgent`**

Write `mas/tests/test_vehicle_agent.py`:

```python
import pytest
from pydantic import ValidationError
from mas.models.coordinates import Coordinates
from mas.models.vehicle_agent import VehicleAgent


def test_vehicle_agent_valid_with_coordinates_object():
    loc = Coordinates(longitude=101.5810, latitude=3.0820)
    agent = VehicleAgent(
        vehicle_id="V-004",
        cargo_priority=5,
        current_location=loc
    )
    assert agent.vehicle_id == "V-004"
    assert agent.cargo_priority == 5
    assert agent.current_location.longitude == 101.5810
    assert agent.current_location.latitude == 3.0820
    assert agent.current_location.to_coords() == [101.5810, 3.0820]


def test_vehicle_agent_valid_with_array_coords():
    agent = VehicleAgent(
        vehicle_id="V-005",
        cargo_priority=1,
        current_location=[101.6500, 3.1100]  # Array coerced into Coordinates
    )
    assert agent.vehicle_id == "V-005"
    assert agent.cargo_priority == 1
    assert agent.current_location.to_coords() == [101.6500, 3.1100]


def test_vehicle_agent_cargo_priority_boundaries():
    # Priority 1 (minimum allowed)
    agent_p1 = VehicleAgent(vehicle_id="V-001", cargo_priority=1, current_location=[101.50, 3.00])
    assert agent_p1.cargo_priority == 1

    # Priority 5 (maximum allowed)
    agent_p5 = VehicleAgent(vehicle_id="V-002", cargo_priority=5, current_location=[101.50, 3.00])
    assert agent_p5.cargo_priority == 5

    # Priority 0 (< 1) must fail
    with pytest.raises(ValidationError) as exc_info:
        VehicleAgent(vehicle_id="V-003", cargo_priority=0, current_location=[101.50, 3.00])
    assert "cargo_priority" in str(exc_info.value)

    # Priority 6 (> 5) must fail
    with pytest.raises(ValidationError) as exc_info:
        VehicleAgent(vehicle_id="V-003", cargo_priority=6, current_location=[101.50, 3.00])
    assert "cargo_priority" in str(exc_info.value)


def test_vehicle_agent_empty_vehicle_id():
    with pytest.raises(ValidationError) as exc_info:
        VehicleAgent(vehicle_id="", cargo_priority=3, current_location=[101.50, 3.00])
    assert "vehicle_id" in str(exc_info.value)


def test_vehicle_agent_whitespace_vehicle_id():
    with pytest.raises(ValidationError) as exc_info:
        VehicleAgent(vehicle_id="   ", cargo_priority=3, current_location=[101.50, 3.00])
    assert "vehicle_id cannot be blank" in str(exc_info.value)


def test_vehicle_agent_invalid_location_propagation():
    with pytest.raises(ValidationError) as exc_info:
        VehicleAgent(
            vehicle_id="V-004",
            cargo_priority=4,
            current_location=[101.20, 3.00]  # Longitude out of Klang Valley
        )
    assert "longitude" in str(exc_info.value)
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
PYTHONPATH=. ./mas/venv/bin/pytest mas/tests/test_vehicle_agent.py -v
```
Expected: FAIL with `ModuleNotFoundError: No module named 'mas.models.vehicle_agent'`.

- [ ] **Step 3: Implement `VehicleAgent` model in `mas/models/vehicle_agent.py`**

Write `mas/models/vehicle_agent.py`:

```python
from pydantic import BaseModel, Field, field_validator
from mas.models.coordinates import Coordinates


class VehicleAgent(BaseModel):
    """
    Autonomous Vehicle Agent base model representing an active fleet vehicle.
    Tracks vehicle identity, cargo priority for right-of-way contention, and current spatial location.
    """
    vehicle_id: str = Field(
        ...,
        min_length=1,
        description="Unique vehicle alphanumeric identifier (e.g., 'V-004')"
    )
    cargo_priority: int = Field(
        ...,
        ge=1,
        le=5,
        description="Cargo priority level: 1 (Standard Dry Goods) to 5 (Critical Medical Supplies)"
    )
    current_location: Coordinates = Field(
        ...,
        description="Current vehicle GPS position strictly in WGS 84 [longitude, latitude] within Klang Valley"
    )

    @field_validator("vehicle_id")
    @classmethod
    def validate_non_blank_id(cls, v: str) -> str:
        trimmed = v.strip()
        if not trimmed:
            raise ValueError("vehicle_id cannot be blank")
        return trimmed
```

Update `mas/models/__init__.py`:

```python
from .coordinates import Coordinates
from .vehicle_agent import VehicleAgent

__all__ = ["Coordinates", "VehicleAgent"]
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
PYTHONPATH=. ./mas/venv/bin/pytest mas/tests/test_vehicle_agent.py -v
```
Expected: PASS (all 6 tests passing).

- [ ] **Step 5: Commit Task 3 files**

Run:
```bash
git add mas/models/vehicle_agent.py mas/models/__init__.py mas/tests/test_vehicle_agent.py
git commit -m "feat(mas): implement VehicleAgent domain model with cargo priority validation"
```

---

### Task 4: Initialize Barebones FastMCP Stdio Server (TDD)

**Files:**
- Create: `mas/server.py`
- Create: `mas/tests/test_server.py`

**Interfaces:**
- Consumes: `Coordinates` and `VehicleAgent` from `mas.models`.
- Produces: Running FastMCP server with `stdio` transport, exposing `ping` and `get_agent_status` tools.

- [ ] **Step 1: Write the failing unit tests for FastMCP server**

Write `mas/tests/test_server.py`:

```python
import pytest
from mas.server import mcp, ping, get_agent_status
from pydantic import ValidationError


def test_mcp_server_instance():
    assert mcp.name == "ASTRA MAS Engine"


def test_ping_tool():
    result = ping()
    assert isinstance(result, dict)
    assert result["status"] == "UP"
    assert result["service"] == "ASTRA MAS Engine"
    assert result["transport"] == "stdio"


def test_get_agent_status_tool_valid():
    result = get_agent_status(
        vehicle_id="V-004",
        cargo_priority=5,
        current_coords=[101.5810, 3.0820]
    )
    assert result["vehicle_id"] == "V-004"
    assert result["cargo_priority"] == 5
    assert result["current_coords"] == [101.5810, 3.0820]
    assert result["status"] == "READY"


def test_get_agent_status_tool_invalid_priority():
    with pytest.raises(ValidationError):
        get_agent_status(
            vehicle_id="V-004",
            cargo_priority=99,
            current_coords=[101.5810, 3.0820]
        )


def test_get_agent_status_tool_inverted_coords():
    # Inverted coordinates [lat, lng] must fail
    with pytest.raises(ValidationError):
        get_agent_status(
            vehicle_id="V-004",
            cargo_priority=5,
            current_coords=[3.0820, 101.5810]
        )
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
PYTHONPATH=. ./mas/venv/bin/pytest mas/tests/test_server.py -v
```
Expected: FAIL with `ModuleNotFoundError: No module named 'mas.server'`.

- [ ] **Step 3: Implement `mas/server.py`**

Write `mas/server.py`:

```python
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
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
PYTHONPATH=. ./mas/venv/bin/pytest mas/tests/test_server.py -v
```
Expected: PASS (all 5 tests passing).

- [ ] **Step 5: Run full test suite across the entire MAS package**

Run:
```bash
PYTHONPATH=. ./mas/venv/bin/pytest mas/tests/ -v
```
Expected: PASS (all 18 unit tests passing).

- [ ] **Step 6: Commit Task 4 files**

Run:
```bash
git add mas/server.py mas/tests/test_server.py
git commit -m "feat(mas): initialize FastMCP stdio server with ping and get_agent_status tools"
```

---

## Plan Self-Review Checklist

- [x] **Spec Coverage:**
  - Virtual environment & requirements (`fastmcp`, `pydantic`, `python-dotenv`) -> Task 1
  - `VehicleAgent` Pydantic model (`vehicle_id`, `cargo_priority: 1-5`, `current_location: [lng, lat]`) -> Tasks 2 & 3
  - FastMCP `stdio` server (`mas/server.py`) -> Task 4
  - `.gitignore` rules (venv, .env, pycache) -> Task 1
- [x] **No Placeholders:** All code snippets, commands, assertions, and tests are complete with no "TODO", "TBD", or placeholders.
- [x] **Type Consistency:** `Coordinates.to_coords()` consistently returns `list[float]`, `cargo_priority` is consistently constrained `ge=1, le=5`, and `current_location` accepts either `Coordinates` object or `[lng, lat]` list/tuple.
- [x] **Spatial Order Rigor:** Inverted coordinates `[lat, lng]` explicitly tested and verified to fail validation across all test suites.
