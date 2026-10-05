# Dynamic Supply Chain & Logistics Rerouting Engine

An event-driven, AI-orchestrated logistics platform designed to resolve the computational bottlenecks of traditional static routing systems. 

This project operates as a Decoupled Modular Monolith, integrating a high-performance spatial state manager with an autonomous Multi-Agent System (MAS). By programmatically ingesting high-frequency geographic anomalies, the engine recalculates optimal paths mid-transit and allows independent agentic nodes to negotiate overlapping route conflicts in real time, successfully mitigating costly "dead mileage" in supply chain operations.

### 🏗️ Tech Stack
* **Backend State Manager:** Java, Spring Boot, Spring Data JPA
* **Spatial Database:** PostgreSQL with PostGIS extensions
* **Routing Engine:** GraphHopper API
* **AI Orchestration:** Python, Multi-Agent System (MAS), Model Context Protocol (MCP)
* **Frontend Dashboard:** Angular V20, Tailwind V4, Mapbox GL JS
* **Infrastructure:** Docker, AWS (EC2, Elastic Load Balancer)

### ✨ Key Features
* **Real-Time Telemetry Ingestion:** Processes high-frequency GPS coordinates and network disruptions without database transaction gridlocks.
* **Dynamic Spatial Rerouting:** Instantly recalculates active GraphHopper routing geometries when a vehicle's trajectory intersects with a network anomaly.
* **Autonomous Conflict Negotiation:** Utilizes Python-based AI agents communicating via the MCP bridge to intelligently resolve overlapping fleet conflicts without manual dispatcher intervention.
* **High-Fidelity Visual State:** An Angular-powered administrative dashboard leveraging Mapbox GL JS to render active fleets, synthetic disruptions, and optimal routing vector lines.