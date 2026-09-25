# 🚢 Cargo Ship Route and Load Tracker

A web application that finds the shortest sea route for a cargo ship visiting multiple ports, and tracks how much cargo is delivered and remaining at every stop — visualized on an interactive, Google-Maps-style map.

Built as a course project for **CSE 213 – Advanced Java Programming**, SRM University AP.

---

## What it does

- **Pick any ports.** Choose a start port and 2 to 5 destination ports from 48+ major world ports, or click anywhere in the ocean to add your own custom port.
- **Finds the shortest route automatically.** Instead of just visiting ports in the order you pick them, the app tries every possible visiting order (a classic Travelling Salesperson Problem) and calculates the real sea distance for each, using the **Eurostat SeaRoute** library — not straight lines through land, but actual maritime shipping lanes.
- **Tracks cargo delivery.** Enter how many boxes the ship carries (default 24,000) and how many are unloaded at each port. The app validates the numbers and shows exactly how many boxes remain on the ship after each stop.
- **Animates the voyage.** Click "Start Voyage" and watch the ship sail along the calculated route, pausing at each port to unload cargo and update the numbers live.
- **Clear, color-coded map.** Every leg of the journey is drawn in its own color with direction arrows, distance labels, and a "Route Legs" panel so the whole journey is easy to follow at a glance.

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3, Maven |
| Route calculation | [Eurostat SeaRoute](https://github.com/eurostat/searoute) (offline maritime network engine) |
| Frontend | HTML, CSS, JavaScript |
| Map | [Leaflet.js](https://leafletjs.com/) + [OpenStreetMap](https://www.openstreetmap.org/) (free, no API key required) |

---

## How it works

1. **Model layer** — `Port`, `Ship`, `Cargo`, and `Route` classes represent the real-world entities.
2. **Service layer**
   - `SeaRoutingService` calculates the real sea distance and path between any two ports using Eurostat SeaRoute, and caches each port pair so it's only calculated once.
   - `RouteOptimizationService` tries every possible visiting order of the chosen destinations and picks the one with the shortest total distance.
   - `CargoService` validates that the cargo unloaded at every stop adds up correctly and tracks what remains on the ship.
3. **Controller layer** — `PortController` and `RouteController` expose this as a REST API.
4. **Frontend** — fetches the calculated route as GeoJSON, draws it on a Leaflet map with colored legs and arrows, and animates the ship sailing along it.

---

## Running it locally

**Requirements:** Java 17+ and Maven.

```bash
git clone https://github.com/DevasisBeniya/cargo-ship-route-tracker.git
cd cargo-ship-route-tracker
mvn spring-boot:run
```

Then open **http://localhost:8081** in your browser.

---

## Team — Pirates

| Name | Registration No. |
|---|---|
| B. Devasis | AP25110010224 |
| L. Pranay Naik | AP25110010778 |
| Prathipati Bhargavaram | AP25110010310 |

**Faculty:** Dr. Binu Jose A

---

## Acknowledgements

- [Eurostat SeaRoute](https://github.com/eurostat/searoute) for the maritime routing engine and network data.
- [OpenStreetMap](https://www.openstreetmap.org/) contributors for the map data.
- [Leaflet.js](https://leafletjs.com/) for the mapping library.
