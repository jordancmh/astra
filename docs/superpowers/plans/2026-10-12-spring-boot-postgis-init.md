# Spring Boot 4 PostGIS Initialization Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Initialize the Java Spring Boot 4.0.8 backend project in `/backend`, configure `application.yml` to connect to the containerized PostGIS spatial database, establish the modular package structure, and verify with a PostGIS integration test.

**Architecture:** Spring Boot 4.0.8 application scaffolded inside `/backend` with Maven wrapper, configured to connect to PostgreSQL 16 + PostGIS 3.4 in Docker. Package structure establishes domain boundaries per AGENTS.md (`telemetry`, `routing`, `anomaly`, `mcp`, `state`, `common`).

**Tech Stack:** Java 21, Spring Boot 4.0.8, Spring Data JPA, Hibernate Spatial, PostgreSQL 16 + PostGIS 3.4, Docker Compose.

## Global Constraints
- Target directory: `/backend`
- Group: `com.astra`, Artifact: `engine`, Package: `com.astra.engine`
- Java version: 21
- Spring Boot version: 4.0.8
- Port mapping: Host port 5433 -> Container port 5432 in `docker-compose.yml` to prevent conflict with any existing host Postgres service
- Connection URL: `${SPRING_DATASOURCE_URL:jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5433}/astra_db}`
- Hibernate DDL mode: `ddl-auto: update`
- Each domain package must contain a `package-info.java`
- Integration test in `AstraEngineApplicationTests` must assert non-null, non-empty result for `SELECT PostGIS_Version();`

---

### Task 1: Update `docker-compose.yml` Host Port to 5433

**Files:**
- Modify: `docker-compose.yml`

**Interfaces:**
- Produces: PostgreSQL container mapped to host port 5433.

- [ ] **Step 1: Update port mapping in `docker-compose.yml`**

Change `- "5432:5432"` to `- "5433:5432"`:
```yaml
    ports:
      - "5433:5432"
```

- [ ] **Step 2: Restart container and verify port 5433 access**

Run:
```bash
docker compose up -d postgres
sleep 2
PGPASSWORD=astra_password psql -h localhost -p 5433 -U astra_user -d astra_db -c "SELECT PostGIS_Version();"
```
Expected: `3.4 USE_GEOS=1 USE_PROJ=1 USE_STATS=1`

- [ ] **Step 3: Commit port mapping update**

```bash
git add docker-compose.yml
git commit -m "chore(infra): map postgis container to host port 5433 to avoid port conflicts"
```

---

### Task 2: Scaffold Spring Boot 4.0.8 Project in `/backend`

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties`
- Create: `backend/src/main/java/com/astra/engine/AstraEngineApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/java/com/astra/engine/AstraEngineApplicationTests.java`

- [ ] **Step 1: Extract project scaffold from Spring Initializr**

Download and extract Spring Boot 4.0.8 starter into `/backend`:
```bash
curl -s "https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=4.0.8&baseDir=engine&groupId=com.astra&artifactId=engine&name=AstraEngine&description=Dynamic+Supply+Chain+%26+Logistics+Rerouting+Engine&packageName=com.astra.engine&javaVersion=21&dependencies=web,data-jpa,postgresql" -o /tmp/astra-starter.zip
mkdir -p /tmp/astra-unpack && unzip -q /tmp/astra-starter.zip -d /tmp/astra-unpack
cp -r /tmp/astra-unpack/engine/* backend/
cp -r /tmp/astra-unpack/engine/.* backend/ 2>/dev/null || true
rm -rf /tmp/astra-unpack /tmp/astra-starter.zip
chmod +x backend/mvnw
```

- [ ] **Step 2: Configure `backend/pom.xml` with `hibernate-spatial`**

Add `org.hibernate.orm:hibernate-spatial` (version managed by Spring Boot parent BOM):
```xml
		<dependency>
			<groupId>org.hibernate.orm</groupId>
			<artifactId>hibernate-spatial</artifactId>
		</dependency>
```

- [ ] **Step 3: Configure `backend/src/main/resources/application.yml`**

Remove default `application.properties` and write `application.yml`:
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

- [ ] **Step 4: Create `package-info.java` in domain packages**

Create package directories and `package-info.java` files under `backend/src/main/java/com/astra/engine/`:
- `telemetry/package-info.java`
- `routing/package-info.java`
- `anomaly/package-info.java`
- `mcp/package-info.java`
- `state/package-info.java`
- `common/package-info.java`

Content template for each:
```java
package com.astra.engine.<module>;
```

- [ ] **Step 5: Write PostGIS integration test in `AstraEngineApplicationTests.java`**

```java
package com.astra.engine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AstraEngineApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void postgisExtensionIsActive() {
		String version = jdbcTemplate.queryForObject("SELECT PostGIS_Version();", String.class);
		assertThat(version).isNotNull().isNotEmpty();
	}

}
```

- [ ] **Step 6: Run tests to verify connection and PostGIS**

Run:
```bash
cd backend && ./mvnw test
```
Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

- [ ] **Step 7: Commit backend scaffold and configuration**

```bash
git add backend/
git commit -m "feat(backend): initialize spring boot project with postgis connection and modular packages"
```
