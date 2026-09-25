package com.cargotracker.controller;

import com.cargotracker.dto.RouteCalculationRequest;
import com.cargotracker.dto.RouteCalculationResponse;
import com.cargotracker.model.Cargo;
import com.cargotracker.model.Port;
import com.cargotracker.model.Route;
import com.cargotracker.model.Ship;
import com.cargotracker.service.CargoService;
import com.cargotracker.service.PortService;
import com.cargotracker.service.RouteOptimizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REST Controller handling maritime route calculation, TSP optimization,
 * and cargo manifest tracking.
 */
@RestController
@RequestMapping("/api/route")
public class RouteController {

    private static final Logger log = LoggerFactory.getLogger(RouteController.class);

    private final PortService portService;
    private final RouteOptimizationService routeOptimizationService;
    private final CargoService cargoService;

    public RouteController(PortService portService,
                           RouteOptimizationService routeOptimizationService,
                           CargoService cargoService) {
        this.portService = portService;
        this.routeOptimizationService = routeOptimizationService;
        this.cargoService = cargoService;
    }

    @PostMapping("/calculate")
    public ResponseEntity<RouteCalculationResponse> calculateOptimalRoute(@RequestBody RouteCalculationRequest request) {
        log.info("Received route calculation request: startPort={}, destinations={}, capacity={}",
                request.getStartPortId(), request.getDestinationPortIds(), request.getShipCapacity());

        int shipCapacity = request.getShipCapacity() > 0 ? request.getShipCapacity() : CargoService.DEFAULT_CAPACITY;

        // 1. Basic validation
        if (request.getStartPortId() == null || request.getStartPortId().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(RouteCalculationResponse.error("Please select a valid start port."));
        }

        if (request.getDestinationPortIds() == null ||
                request.getDestinationPortIds().size() < 2 ||
                request.getDestinationPortIds().size() > 5) {
            return ResponseEntity.badRequest().body(RouteCalculationResponse.error(
                    "Please select between 2 and 5 destination ports (selected: " +
                            (request.getDestinationPortIds() != null ? request.getDestinationPortIds().size() : 0) + ")."
            ));
        }

        // 2. Resolve Start Port
        Optional<Port> startPortOpt = portService.findById(request.getStartPortId());
        if (startPortOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(RouteCalculationResponse.error(
                    "Start port '" + request.getStartPortId() + "' not found in catalog."
            ));
        }
        Port startPort = startPortOpt.get();

        // 3. Resolve Destination Ports
        List<Port> destinationPorts = new ArrayList<>();
        List<String> allPortIds = new ArrayList<>();
        allPortIds.add(startPort.getId());

        for (String destId : request.getDestinationPortIds()) {
            if (destId.equals(startPort.getId())) {
                return ResponseEntity.badRequest().body(RouteCalculationResponse.error(
                        "Start port ('" + startPort.getName() + "') cannot also be chosen as a destination port."
                ));
            }
            if (allPortIds.contains(destId)) {
                return ResponseEntity.badRequest().body(RouteCalculationResponse.error(
                        "Duplicate destination port selected: " + destId
                ));
            }
            Optional<Port> destOpt = portService.findById(destId);
            if (destOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(RouteCalculationResponse.error(
                        "Destination port '" + destId + "' not found in catalog."
                ));
            }
            destinationPorts.add(destOpt.get());
            allPortIds.add(destId);
        }

        // 4. Validate Cargo Allocation (Start Port + All Destination Ports must equal shipCapacity)
        List<String> cargoErrors = cargoService.validateAllocations(allPortIds, request.getCargoAllocations(), shipCapacity);
        if (!cargoErrors.isEmpty()) {
            log.warn("Cargo validation failed: {}", cargoErrors);
            return ResponseEntity.badRequest().body(RouteCalculationResponse.error(cargoErrors));
        }

        // 5. Calculate Shortest Route via Eurostat SeaRoute and TSP Permutations
        Route optimalRoute;
        try {
            optimalRoute = routeOptimizationService.findShortestRoute(startPort, destinationPorts);
        } catch (Exception e) {
            log.warn("Sea route optimization warning/error: {}", e.getMessage());
            // Return user-friendly warning message without crashing
            return ResponseEntity.ok(RouteCalculationResponse.error(
                    "Maritime Navigation Warning: " + e.getMessage()
            ));
        }

        // 6. Apply Cargo Manifest to the ordered stops
        Cargo cargoManifest = cargoService.applyCargoToStops(
                optimalRoute.getStops(),
                request.getCargoAllocations(),
                shipCapacity
        );

        // 7. Instantiate Ship
        Ship vessel = new Ship(Ship.SHIP_NAME, shipCapacity);
        vessel.setCurrentPort(startPort.getName());
        vessel.setStatus("Ready for Voyage");

        // 8. Assemble response
        RouteCalculationResponse response = new RouteCalculationResponse();
        response.setSuccess(true);
        response.setMessage("Optimal maritime route successfully calculated.");
        response.setRoute(optimalRoute);
        response.setShip(vessel);
        response.setCargo(cargoManifest);
        response.setStops(optimalRoute.getStops());

        return ResponseEntity.ok(response);
    }
}
