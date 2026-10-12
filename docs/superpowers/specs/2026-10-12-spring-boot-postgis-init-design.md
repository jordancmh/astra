# Design Spec: Initialize Spring Boot Project with PostGIS Connection

## 1. Overview
Sprint 1 Task 3 for the Astra project. Scaffold a Java 21 Spring Boot 3.x project inside `/backend` with Maven wrapper, configure `application.yml` to connect to the containerized PostGIS database, and establish the modular package structure per AGENTS.md.

## 2. Technical Requirements
- Generate a Spring Boot 3.4.x project via Spring Initializr (Maven, Java 21, jar packaging).
- Configure `application.yml` with parameterized DB host (`${DB_HOST:localhost}`) for WSL localhost dev and future container-to-container networking.
- Use `ddl-auto: update` so Hibernate auto-creates tables when JPA entities are defined in Task 4.
- Add Hibernate Spatial + JTS Core dependencies for PostGIS geometry type support.
- Future JTS entity fields must use explicit `@Column(columnDefinition = "geometry(Point, 4326)")` annotations to ensure correct PostGIS column types (enforced in Task 4, not this task).
- Establish empty domain packages: `telemetry`, `routing`, `anomaly`, `mcp`, `state`, `common`.
- Verify: application context loads and connects to the running PostGIS container.

## 3. Project Configuration

### 3.1 Maven Coordinates
- **Group ID:** `com.astra`
- **Artifact ID:** `engine`
- **Package:** `com.astra.engine`
- **Java Version:** 21
- **Spring Boot Version:** 3.4.x (latest stable)

### 3.2 Dependencies
| Dependency | Scope | Purpose |
|---|---|---|
| `spring-boot-starter-web` | compile | REST controller support |
| `spring-boot-starter-data-jpa` | compile | Spring Data JPA + Hibernate ORM |
| `postgresql` | runtime | PostgreSQL JDBC driver |
| `org.hibernate.orm:hibernate-spatial` | compile | PostGIS geometry type mapping |
| `org.locationtech.jts:jts-core` | compile | JTS Point, LineString, Polygon types |
| `spring-boot-starter-test` | test | JUnit 5, Mockito, Spring Test |

### 3.3 `application.yml`
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/astra_db
    username: astra_user
    password: astra_password
    driver-class-name: org.postgresql.Driver
  jpa:
    database-platform: org.hibernate.spatial.dialect.postgis.PostgisPG10Dialect
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

### 3.4 Package Structure
```
backend/src/main/java/com/astra/engine/
├── AstraEngineApplication.java
├── telemetry/
├── routing/
├── anomaly/
├── mcp/
├── state/
└── common/
```

### 3.5 Verification Criteria
1. `docker compose up -d postgres` → container reaches healthy state.
2. `./mvnw test` from `/backend` → Spring application context loads, Hibernate connects to PostGIS, tests pass.
3. `./mvnw spring-boot:run` from `/backend` → starts on port 8080, logs confirm DB connection.
