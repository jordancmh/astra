# Docker Compose PostGIS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure a localized PostgreSQL instance pre-configured with PostGIS spatial extension in `docker-compose.yml` for Sprint 1 Task 2.

**Architecture:** A standalone Docker Compose service running official `postgis/postgis:16-3.4` with persistent local volume storage, container healthcheck, and an isolated bridge network ready for Spring Boot backend integration.

**Tech Stack:** Docker, Docker Compose, PostgreSQL 16, PostGIS 3.4

## Global Constraints
- Database service must use image `postgis/postgis:16-3.4`
- Default development credentials: DB `astra_db`, User `astra_user`, Password `astra_password`
- Port `5432:5432` mapped to host
- Data volume `postgres_data` mapped to `/var/lib/postgresql/data`
- Container healthcheck must test `pg_isready -U astra_user -d astra_db`
- Dedicated bridge network `astra-network`

---

### Task 1: Configure PostGIS Service in `docker-compose.yml`

**Files:**
- Modify: `docker-compose.yml`

**Interfaces:**
- Produces: Service `postgres` exposing port 5432, database `astra_db`, user `astra_user`, password `astra_password`, network `astra-network`.

- [ ] **Step 1: Write `docker-compose.yml` content**

Populate `docker-compose.yml` with:
```yaml
services:
  postgres:
    image: postgis/postgis:16-3.4
    container_name: astra-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: astra_db
      POSTGRES_USER: astra_user
      POSTGRES_PASSWORD: astra_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U astra_user -d astra_db"]
      interval: 5s
      timeout: 5s
      retries: 5
    networks:
      - astra-network

volumes:
  postgres_data:
    driver: local

networks:
  astra-network:
    driver: bridge
```

- [ ] **Step 2: Validate YAML syntax**

Run: `python3 -c "import yaml; yaml.safe_load(open('docker-compose.yml'))" && echo "YAML VALID"`
Expected output: `YAML VALID`

- [ ] **Step 3: Commit changes**

```bash
git add docker-compose.yml
git commit -m "feat: configure localized postgis service in docker-compose"
```
