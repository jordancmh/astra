# Design Spec: Localized PostgreSQL with PostGIS Spatial Extension

## 1. Overview
This specification details the Docker Compose configuration for deploying a localized PostgreSQL instance pre-configured with the PostGIS spatial extension for the Astra Dynamic Supply Chain & Logistics Rerouting Engine (Sprint 1, Task 2).

## 2. Technical Requirements
- Deploy a standalone PostgreSQL container with PostGIS pre-installed and enabled.
- Ensure zero-config development startup using default development credentials.
- Persist data across container restarts using a Docker named volume.
- Expose a container healthcheck for orchestrating downstream container startup (e.g. Spring Boot backend).
- Create a dedicated Docker network to enable multi-container communication in future sprints.

## 3. Architecture & Configuration

### 3.1 Service: `postgres`
- **Service Name:** `postgres`
- **Container Name:** `astra-postgres`
- **Image:** `postgis/postgis:16-3.4`
- **Restart:** `unless-stopped`
- **Environment Variables:**
  - `POSTGRES_DB`: `astra_db`
  - `POSTGRES_USER`: `astra_user`
  - `POSTGRES_PASSWORD`: `astra_password`
- **Port Mapping:** `5432:5432`
- **Volumes:**
  - `postgres_data:/var/lib/postgresql/data`
- **Healthcheck:**
  - Command: `["CMD-SHELL", "pg_isready -U astra_user -d astra_db"]`
  - Interval: `5s`
  - Timeout: `5s`
  - Retries: `5`
- **Networks:**
  - `astra-network`

### 3.2 Top-Level Infrastructure
- **Volumes:**
  - `postgres_data`: Standard local storage volume
- **Networks:**
  - `astra-network`: Bridge network for backend and agent communication

## 4. Downstream Integration (Task 3 & 4)
- **JDBC Connection String:** `jdbc:postgresql://localhost:5432/astra_db`
- **Credentials:** Username `astra_user`, Password `astra_password`
- **Hibernate / Spatial Compatibility:** Hibernate Spatial (`org.hibernate.spatial.dialect.postgis.PostgisPG95Dialect` or modern dialect autodetection in Hibernate 6.x / Spring Boot 3.x)
