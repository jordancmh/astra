# Python MAS Environment Design Specification

## 1. Overview & Objectives
This specification addresses GitHub Issue #12: **"Initialize Python MAS Environment"**.
The objective is to establish the foundation for ASTRA's decentralized Multi-Agent System (MAS) in a newly designated `/mas` root directory. The MAS framework enables autonomous vehicle agents to negotiate detour corridors when road anomalies disrupt active routes (`FR 3.1`–`FR 3.3`, `NFR 1.2`, `NFR 2.1`–`NFR 2.2`).

This initial phase establishes:
1. An isolated Python 3.12+ virtual environment (`venv`) with dependencies managed via `mas/requirements.txt` (`fastmcp`, `pydantic>=2.0`, `python-dotenv`, `pytest`).
2. A strictly-validated Pydantic domain model hierarchy featuring `Coordinates` and `VehicleAgent`, enforcing the non-negotiable `[longitude, latitude]` order (SRID 4326) within the Klang Valley geographic bounding box (`Lat: 2.90 to 3.30`, `Lng: 101.40 to 101.90`).
3. A barebones FastMCP server (`mas/server.py`) configured with `stdio` transport, ready to be spawned and communicated with as a subprocess by the Spring Boot backend (`com.astra.engine.mcp`).
4. Comprehensive `.gitignore` rules shielding the repository from Python virtual environments, bytecode caches, and local `.env` files.

---

## 2. Directory Layout & Architecture
Following **Approach 1 (Modular Domain Package)**, the `/mas` directory is organized as follows:

```text
astra/
├── .gitignore
├── mas/
│   ├── .env.example
│   ├── requirements.txt
│   ├── models/
│   │   ├── __init__.py
│   │   ├── coordinates.py
│   │   └── vehicle_agent.py
│   ├── server.py
│   └── tests/
│       ├── __init__.py
│       ├── test_coordinates.py
│       ├── test_vehicle_agent.py
│       └── test_server.py
```

---

## 3. Data Models & Validation Rules

### 3.1 Coordinates Model (`mas/models/coordinates.py`)
Per `AGENTS.md` §6.A and `documents/01-architecture-rules.md`, all coordinates must strictly observe `[longitude, latitude]` ordering (WGS 84 / SRID 4326).
- **Longitude Range ($X$):** `101.40` to `101.90` (inclusive).
- **Latitude Range ($Y$):** `2.90` to `3.30` (inclusive).
- **Flexible Deserialization:** Must accept both standard dictionary inputs (`{"longitude": 101.55, "latitude": 3.12}`) and GeoJSON-style coordinate arrays (`[101.55, 3.12]` or `(101.55, 3.12)`).
- **Inversion Safety:** If a caller erroneously supplies `[latitude, longitude]` (e.g., `[3.12, 101.55]`), the longitude validation (`3.12 < 101.40`) immediately rejects the payload with a descriptive `ValidationError`.
- **Export Helper:** Provides `.to_coords() -> list[float]` returning `[self.longitude, self.latitude]`.

### 3.2 VehicleAgent Model (`mas/models/vehicle_agent.py`)
- **`vehicle_id` (str):** Unique non-empty string identifier (e.g., `"V-004"`).
- **`cargo_priority` (int):** Priority score between `1` and `5` inclusive:
  - `1`: Standard Dry Goods
  - `2`: Retail Goods
  - `3`: Perishable Groceries
  - `4`: High-Value Electronics
  - `5`: Critical Pharmaceuticals / Vaccines
- **`current_location` (Coordinates):** Current spatial location within the Klang Valley bounding box.

---

## 4. FastMCP Server Specification (`mas/server.py`)

### 4.1 Server Transport & Lifecycle
- Uses FastMCP (`from fastmcp import FastMCP`).
- Configured to run over standard input/output (`transport="stdio"`).
- In future tasks, the Spring Boot backend (`com.astra.engine.mcp`) will spawn `python -m mas.server` (or `python server.py`) via Java `ProcessBuilder`, communicating seamlessly using JSON-RPC 2.0 over standard I/O pipes.

### 4.2 Barebones Tools
1. **`ping()`**:
   - Parameters: none.
   - Returns: `{"status": "UP", "service": "ASTRA MAS Engine", "protocol": "fastmcp-stdio"}`.
2. **`get_agent_status(vehicle_id: str, cargo_priority: int, current_coords: list[float])`**:
   - Parameters:
     - `vehicle_id`: string
     - `cargo_priority`: int (1–5)
     - `current_coords`: list of two floats `[longitude, latitude]`
   - Behavior: Instantiates a `VehicleAgent` to validate constraints.
   - Returns: Validated agent summary dictionary.

---

## 5. Security & Isolation Guardrails
- **No Direct Database Access:** In strict compliance with `NFR 2.1` & `NFR 2.2`, `mas/requirements.txt` does not include `psycopg2`, `asyncpg`, or SQL drivers. Python MAS communicates with the database solely through Spring Boot MCP tools.
- **Git Hygiene:** Local virtual environments (`venv/`, `.venv/`), environment variables (`.env`), and Python cache artifacts (`__pycache__/`, `.pytest_cache/`) are ignored in `.gitignore`.

---

## 6. Testing Strategy
- Framework: `pytest` with 100% test coverage over:
  1. `test_coordinates.py`: Valid bounds, invalid bounds, tuple parsing, inverted coordinates rejection, `to_coords()` output.
  2. `test_vehicle_agent.py`: Valid instantiation, priority boundaries (0 and 6 rejected), empty ID rejection, nested coordinate parsing.
  3. `test_server.py`: FastMCP instance verification, tool invocation for `ping` and `get_agent_status`.
