# Cargo Ship Route and Load Tracker

A full-stack maritime logistics web application featuring the flagship vessel **MV Pirates Voyager**. Built with **Java 17**, **Spring Boot 3 (Maven)**, and an interactive **Leaflet.js + OpenStreetMap** frontend (100% free, open-access, zero Google Maps API keys required).

---

## Key Features

1. **Clean Layered Architecture (`controller`, `service`, `model`)**:
   - **`Port`**, **`Ship`**, **`Cargo`**, and **`Route`** domain models with educational comments.
   - Separate REST controllers (`PortController`, `RouteController`) and business services (`PortService`, `CargoService`, `SeaRoutingService`, `RouteOptimizationService`).
2. **Shortest Sea Route Optimization (Traveling Salesperson Problem)**:
   - Evaluates all candidate visiting sequences for a selected departure port and 2 to 5 international destinations ($N!$ permutations: up to 120 candidate paths).
   - Powered by the official **Eurostat SeaRoute Core 3.6** library using high-resolution maritime shipping networks (MARNET GeoPackage).
   - **Symmetric Pair-Distance Caching**: Calculates distance and geometry between each pair of ports only once ($dist(A, B) = dist(B, A)$) and reuses it across all permutations.
3. **Dynamic Seaport Catalog & Runtime Custom Ports**:
   - Loads 48+ major commercial ports from `src/main/resources/ports.json` across countries (India, Japan, Indonesia, Madagascar, Singapore, UAE, South Africa, Egypt, USA, Australia, Brazil, etc.).
   - Ports in the UI are grouped by Country.
   - **Add Custom Ports at Runtime**: Click anywhere on the ocean map or manually type port name, country, latitude, and longitude. Newly registered ports can immediately be chosen as departure or destination stops.
4. **Resilient Sea Route Error Handling**:
   - If a port is too far inland or disconnected from navigable shipping lanes, a clear, friendly warning is displayed without crashing the application.
5. **Configurable Cargo Load & Invariant Validation**:
   - Configurable ship total capacity (default: **24,000 container boxes**).
   - **Start Port Unloading**: The departure port (e.g. India) can unload boxes before setting sail.
   - Strictly validates that the total unloaded boxes across **ALL stops** (including departure) equals the ship's load. Displays an instant live error/warning if unbalanced.
   - Interactive Stop Schedule Table displays boxes unloaded, cargo left on the ship after departure, and live status for each stop.
6. **Animated Voyage Simulation**:
   - Animated vessel (**MV Pirates Voyager**) with automated heading rotation sailing along the GeoJSON sea track.
   - Realistic docking and unloading pauses at each port stop with live delivery progress updates and table status transitions.
   - Simulation controls: Start, Pause, Resume, Reset, and speed multiplier (1x, 2x, 5x).
7. **Academic Documentation & System Design**:
   - Complete Mermaid architecture diagram saved in [`docs/architecture-diagram.md`](docs/architecture-diagram.md) for report inclusion.

---

## Prerequisites

- **Java 17+** (e.g., Eclipse Adoptium Temurin 17)
- **Apache Maven 3.8+** (or use the provided Maven installation)
- **Web Browser** (Chrome, Firefox, Edge)

---

## How to Run the Application

### 1. Set JAVA_HOME to Java 17 (if needed on Windows)
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

### 2. Run with Maven
In the project root directory, run:
```bash
mvn spring-boot:run
```

### 3. Open in Browser
Navigate to:
```
http://localhost:8081
```
*(Port 8081 is configured in `application.properties` to avoid conflicts with background system services on 8080).*

---

## How to Test the Features (Step-by-Step)

1. **Default Itinerary**:
   - Start Port: **India - Port of Mumbai / JNPT**
   - Click **"Demo: Japan + Indonesia + Madagascar"** preset button to select 3 destinations (Tokyo, Jakarta, Toamasina).
2. **Cargo Allocation**:
   - Notice the input for Mumbai (Start Port) and each destination.
   - Click **"⚡ Auto Balance"** to automatically distribute the 24,000 boxes across all 4 stops.
   - Try altering an input to an unbalanced number (e.g. total = 20,000) to observe the live red validation banner preventing calculation.
3. **Calculate Optimal Route**:
   - Click **"🧭 Calculate Shortest Sea Route"**.
   - Observe the Eurostat SeaRoute engine evaluating all visiting orders and selecting the shortest maritime track.
   - Notice the numbered markers (1 for Mumbai, 2..4 for destinations) and the continuous sea polyline on the OpenStreetMap canvas.
4. **Start Voyage Simulation**:
   - Click **"▶ Start Voyage"**.
   - Watch **MV Pirates Voyager** disembark from Mumbai, unload cargo, sail across the Indian Ocean and Pacific, adjust heading rotation, and unload at each port until the voyage completes.
5. **Add a Custom Port**:
   - Click anywhere in the ocean on the map, or click the **"📍 Add Custom Port"** button.
   - Enter a name and country, save it, and select it as a new destination stop!

---

## Project Structure

```
cargo-ship-route-tracker/
├── docs/
│   └── architecture-diagram.md     # Mermaid diagram and design explanations for student reports
├── src/
│   ├── main/
│   │   ├── java/com/cargotracker/
│   │   │   ├── CargoTrackerApplication.java
│   │   │   ├── controller/
│   │   │   │   ├── PortController.java          # GET & POST /api/ports
│   │   │   │   └── RouteController.java         # POST /api/route/calculate
│   │   │   ├── service/
│   │   │   │   ├── PortService.java             # JSON seaport registry & custom ports
│   │   │   │   ├── CargoService.java            # 24k load invariant validation
│   │   │   │   ├── SeaRoutingService.java       # Eurostat SeaRoute & pair distance cache
│   │   │   │   └── RouteOptimizationService.java# TSP brute-force permutations
│   │   │   ├── model/
│   │   │   │   ├── Port.java                    # Port coordinates & stop manifest
│   │   │   │   ├── Ship.java                    # MV Pirates Voyager vessel model
│   │   │   │   ├── Cargo.java                   # Cargo manifest & total load
│   │   │   │   ├── Route.java                   # Stops, total distance & GeoJSON
│   │   │   │   └── RouteSegment.java            # Individual navigational leg
│   │   │   └── dto/
│   │   │       ├── RouteCalculationRequest.java
│   │   │       └── RouteCalculationResponse.java
│   │   └── resources/
│   │       ├── ports.json                       # 48+ International commercial ports
│   │       ├── application.properties           # Spring Boot configuration
│   │       └── static/
│   │           ├── index.html                   # Maritime cockpit UI & Leaflet container
│   │           ├── css/style.css                # Oceanic theme, numbered badges, ship marker
│   │           └── js/app.js                    # Map engine, TSP visualization, simulation
│   └── test/
│       └── java/com/cargotracker/
│           ├── CargoServiceTest.java
│           ├── RouteOptimizationServiceTest.java
│           └── SeaRouteTest.java
├── marnet/                                      # Extracted Eurostat GeoPackage datasets
├── pom.xml                                      # Maven build config (Java 17, UTF-8)
└── README.md
```
