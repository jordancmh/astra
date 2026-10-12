# Design Spec: Initialize Spring Boot Project with PostGIS Connection

## 1. Overview
Sprint 1 Task 3 for the Astra project. Scaffold a Java 21 Spring Boot 4.0.8 project inside `/backend` with Maven wrapper, configure `application.yml` to connect to the containerized PostGIS database, and establish the modular package structure per AGENTS.md.

## 2. Technical Requirements
- Generate a Spring Boot 4.0.8 project via Spring Initializr (Maven, Java 21, jar packaging).
- Configure `application.yml` with parameterized DB port and host (`${DB_HOST:localhost}`, `${DB_PORT:5433}`) to support non-conflicting local WSL dev and container-to-container networking.
- Update `docker-compose.yml` host port mapping to `"5433:5432"` to avoid port conflict with existing host PostgreSQL instances while container internal port remains 5432.
- Use `ddl-auto: update` so Hibernate auto-creates tables when JPA entities are defined in Task 4.
- Add Hibernate Spatial (`org.hibernate.orm:hibernate-spatial`) managed by Spring Boot BOM.
- Explicit JTS entity fields will use `@Column(columnDefinition = "geometry(Point, 4326)")` in future tasks.
- Establish Git-tracked modular packages using `package-info.java`: `telemetry`, `routing`, `anomaly`, `mcp`, `state`, `common`.
- Integration test in `AstraEngineApplicationTests` verifying `SELECT PostGIS_Version()` returns active PostGIS extension.

## 3. Project Configuration

### 3.1 Maven Coordinates
- **Group ID:** `com.astra`
- **Artifact ID:** `engine`
- **Package:** `com.astra.engine`
- **Java Version:** 21
- **Spring Boot Version:** 4.0.8

### 3.2 Dependencies
| Dependency | Scope | Purpose |
|---|---|---|
| `spring-boot-starter-webmvc` | compile | REST controller support |
| `spring-boot-starter-data-jpa` | compile | Spring Data JPA + Hibernate ORM |
| `postgresql` | runtime | PostgreSQL JDBC driver |
| `org.hibernate.orm:hibernate-spatial` | compile (BOM-managed) | PostGIS geometry type mapping |
| `spring-boot-starter-data-jpa-test` | test | Spring Boot JPA Test |
| `spring-boot-starter-webmvc-test` | test | Spring Boot Web Test |

### 3.3 `application.yml`
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5433}/astra_db}
    username: astra_user
    password: astra_password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

### 3.4 Package Structure
Each domain package contains a `package-info.java` so the modular monolith structure is committed to Git:
```
backend/src/main/java/com/astra/engine/
├── AstraEngineApplication.java
├── telemetry/package-info.java
├── routing/package-info.java
├── anomaly/package-info.java
├── mcp/package-info.java
├── state/package-info.java
└── common/package-info.java
```

### 3.5 Verification Criteria
1. `docker compose up -d postgres` (mapped to host port 5433) reaches healthy status.
2. `./mvnw test` from `/backend` loads Spring application context and connects to containerized PostGIS.
3. `AstraEngineApplicationTests` executes `SELECT PostGIS_Version()` successfully via `JdbcTemplate`.
