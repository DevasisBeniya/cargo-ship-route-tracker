# Architecture Design: Cargo Ship Route and Load Tracker

## System Architecture Diagram

```mermaid
flowchart TD
    subgraph Client ["Frontend Client (Browser)"]
        UI["Maritime UI Dashboard (HTML5 / CSS3 / Vanilla JS)"]
        OSM["Leaflet.js + OpenStreetMap Engine"]
        Engine["Voyage Simulation & Ship Animation Engine"]
    end

    subgraph Backend ["Spring Boot 3 Backend (Java 17)"]
        subgraph Controllers ["Controller Layer"]
            PortCtrl["PortController\nGET /api/ports"]
            RouteCtrl["RouteController\nPOST /api/route/calculate"]
        end

        subgraph Services ["Service Layer"]
            PortSvc["PortService\n(International Commercial Seaports)"]
            CargoSvc["CargoService\n(24,000 TEU Allocation & Invariant Validation)"]
            RouteOptSvc["RouteOptimizationService\n(TSP Permutations & Shortest Path)"]
            SeaRouteSvc["SeaRoutingService\n(Eurostat SeaRoute / Marnet Engine & Pair Distance Cache)"]
        end

        subgraph Models ["Domain Models (OOP)"]
            Port["Port Model\n(ID, Name, Country, Lat, Lon, Stop, Unload, Balance)"]
            Ship["Ship Model\n('MV Pirates Voyager', 24k Capacity, Load, Status)"]
            Cargo["Cargo Model\n(Total 24k, Remaining, Delivered, Allocations)"]
            Route["Route Model\n(Ordered Stops, Total Km, Nautical Miles, GeoJSON)"]
        end
    end

    UI -->|1. Fetch Port Catalog| PortCtrl
    PortCtrl --> PortSvc
    UI -->|2. Submit Start Port, 2-3 Destinations & Cargo Unload Counts| RouteCtrl
    RouteCtrl --> CargoSvc
    RouteCtrl --> RouteOptSvc
    RouteOptSvc --> SeaRouteSvc
    SeaRouteSvc -->|Distance Cache Key (A_B)| SeaRouteSvc
    RouteOptSvc --> PortSvc
    RouteCtrl -->|3. Optimized Route (GeoJSON) + Stop Table Schedule| UI
    UI --> OSM
    UI --> Engine
```

---

## Component Descriptions for Academic Presentation

### 1. Presentation Layer (Frontend)
- **Leaflet.js + OpenStreetMap**: Renders realistic ocean maps and maritime paths using open-access tiles (no proprietary Google Maps API or credentials required).
- **Voyage Simulation Engine**: Reads coordinate segments from the backend GeoJSON response, animates the cargo vessel (**MV Pirates Voyager**), computes heading angles, and updates cargo statistics at each port stop.

### 2. Controller Layer (REST API)
- **`PortController`**: Provides endpoint `/api/ports` to supply the frontend with real-world port metadata (coordinates, countries, identifiers).
- **`RouteController`**: Exposes `/api/route/calculate` to handle itinerary planning and cargo validation requests.

### 3. Service Layer (Business Logic)
- **`PortService`**: In-memory repository of major global shipping hubs (e.g. Mumbai/JNPT in India, Tokyo in Japan, Jakarta in Indonesia, Toamasina in Madagascar, Rotterdam, Singapore, etc.).
- **`CargoService`**: Enforces the cargo invariant: the total ship capacity is 24,000 boxes, and the sum of unloads across all stops (including the origin port) must equal exactly 24,000. Computes step-by-step box balances.
- **`SeaRoutingService`**: Interfaces with the Eurostat SeaRoute core library (`eu.europa.ec.eurostat:searoute-core`) to obtain maritime navigational paths across oceans and canals. Implements symmetric pair-distance caching ($dist(A, B) = dist(B, A)$) to ensure each leg is calculated only once.
- **`RouteOptimizationService`**: Implements brute-force permutation search for the Traveling Salesperson Problem (TSP) with a fixed departure port, finding the global shortest sea route across 2 or 3 destinations.

### 4. Domain Models (Object-Oriented Design)
- **`Port`**: Encapsulates geographic attributes (latitude, longitude, name, country) and stop-specific scheduling data (unloaded boxes, remaining boxes, stop order, delivery status).
- **`Ship`**: Represents the vessel **MV Pirates Voyager** with fixed capacity of 24,000 boxes, current cargo load, and voyage status.
- **`Cargo`**: Manages overall cargo lifecycle, allocation mapping, and integrity verification.
- **`Route`**: Holds the final ordered sequence of stops, total distance in kilometers and nautical miles, individual voyage legs, and GeoJSON geometry.
