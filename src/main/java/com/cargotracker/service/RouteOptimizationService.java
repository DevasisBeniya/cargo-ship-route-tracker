package com.cargotracker.service;

import com.cargotracker.model.Port;
import com.cargotracker.model.Route;
import com.cargotracker.model.RouteSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service that solves the Traveling Salesperson Problem (TSP) with a fixed origin.
 *
 * Algorithm description for academic defense:
 * Given a fixed departure Port (e.g., India) and a set of 2 to 5 destination Ports,
 * the service generates all permutations of destination visiting sequences (N! permutations,
 * where 2! = 2, 3! = 6, 4! = 24, 5! = 120).
 *
 * For each permutation, it calculates the cumulative sea voyage distance using the
 * cached Eurostat SeaRoute service. It selects the global minimum-distance itinerary
 * and constructs an ordered multi-stop Route with full GeoJSON geometry for Leaflet.js.
 */
@Service
public class RouteOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(RouteOptimizationService.class);

    private final SeaRoutingService seaRoutingService;

    public RouteOptimizationService(SeaRoutingService seaRoutingService) {
        this.seaRoutingService = seaRoutingService;
    }

    /**
     * Finds the shortest sea route starting at startPort and visiting all destinationPorts.
     *
     * @param startPort origin port (Stop 1)
     * @param destinationPorts set of 2 to 5 destination ports
     * @return optimized Route object
     */
    public Route findShortestRoute(Port startPort, List<Port> destinationPorts) {
        if (startPort == null) {
            throw new IllegalArgumentException("Start port is required.");
        }
        if (destinationPorts == null || destinationPorts.size() < 2 || destinationPorts.size() > 5) {
            throw new IllegalArgumentException("Please select between 2 and 5 destination ports.");
        }

        // Check for duplicates
        Set<String> allIds = new HashSet<>();
        allIds.add(startPort.getId());
        for (Port p : destinationPorts) {
            if (!allIds.add(p.getId())) {
                throw new IllegalArgumentException("Duplicate port selected in route: " + p.getName());
            }
        }

        // Generate all permutations of destination visiting order
        List<List<Port>> permutations = new ArrayList<>();
        generatePermutations(new ArrayList<>(destinationPorts), 0, permutations);
        log.info("Evaluating {} candidate visiting orders for {} destinations starting from {}",
                permutations.size(), destinationPorts.size(), startPort.getName());

        List<Port> bestOrder = null;
        List<RouteSegment> bestSegments = null;
        double minDistanceKm = Double.MAX_VALUE;

        // Evaluate each permutation
        for (List<Port> order : permutations) {
            double currentDistanceKm = 0.0;
            List<RouteSegment> currentSegments = new ArrayList<>();
            Port currentOrigin = startPort;

            for (Port destination : order) {
                RouteSegment segment = seaRoutingService.calculateLeg(currentOrigin, destination);
                currentSegments.add(segment);
                currentDistanceKm += segment.getDistanceKm();
                currentOrigin = destination;
            }

            if (currentDistanceKm < minDistanceKm) {
                minDistanceKm = currentDistanceKm;
                bestOrder = order;
                bestSegments = currentSegments;
            }
        }

        if (bestOrder == null || bestSegments == null) {
            throw new IllegalStateException("Could not compute an optimal sea route.");
        }

        log.info("Optimal visiting sequence determined: Total distance = {} km across {} stops",
                minDistanceKm, bestOrder.size() + 1);

        // Build the final ordered Route object
        Route route = new Route();
        route.setRoutingEngine("Eurostat SeaRoute Core (20km MARNET)");
        route.setTotalDistanceKm(minDistanceKm);
        route.setTotalDistanceNm(minDistanceKm * 0.539957);
        route.setSegments(bestSegments);

        // Populate ordered stops with sequential stop numbers (1-based)
        List<Port> orderedStops = new ArrayList<>();
        Port originCopy = new Port(startPort);
        originCopy.setStopNumber(1);
        orderedStops.add(originCopy);

        int stopNum = 2;
        for (Port dest : bestOrder) {
            Port destCopy = new Port(dest);
            destCopy.setStopNumber(stopNum++);
            orderedStops.add(destCopy);
        }
        route.setStops(orderedStops);

        // Build combined GeoJSON representation
        Map<String, Object> geoJson = buildGeoJsonFeatureCollection(route);
        route.setGeoJson(geoJson);

        return route;
    }

    /**
     * Standard recursive Heap's/backtracking algorithm for generating list permutations.
     */
    private void generatePermutations(List<Port> list, int index, List<List<Port>> result) {
        if (index == list.size() - 1) {
            result.add(new ArrayList<>(list));
            return;
        }
        for (int i = index; i < list.size(); i++) {
            Collections.swap(list, index, i);
            generatePermutations(list, index + 1, result);
            Collections.swap(list, index, i);
        }
    }

    /**
     * Constructs a GeoJSON FeatureCollection containing:
     * 1. A LineString Feature for the entire continuous maritime track.
     * 2. Point Features for each numbered port stop.
     */
    private Map<String, Object> buildGeoJsonFeatureCollection(Route route) {
        Map<String, Object> featureCollection = new LinkedHashMap<>();
        featureCollection.put("type", "FeatureCollection");

        List<Map<String, Object>> features = new ArrayList<>();

        // 1. Maritime Track LineString
        List<List<Double>> allCoordinates = new ArrayList<>();
        for (RouteSegment segment : route.getSegments()) {
            if (segment.getCoordinates() != null) {
                allCoordinates.addAll(segment.getCoordinates());
            }
        }

        Map<String, Object> trackFeature = new LinkedHashMap<>();
        trackFeature.put("type", "Feature");

        Map<String, Object> trackGeometry = new LinkedHashMap<>();
        trackGeometry.put("type", "LineString");
        trackGeometry.put("coordinates", allCoordinates);
        trackFeature.put("geometry", trackGeometry);

        Map<String, Object> trackProperties = new LinkedHashMap<>();
        trackProperties.put("type", "maritimeRoute");
        trackProperties.put("totalDistanceKm", route.getTotalDistanceKm());
        trackProperties.put("totalDistanceNm", route.getTotalDistanceNm());
        trackProperties.put("stopCount", route.getStops().size());
        trackFeature.put("properties", trackProperties);

        features.add(trackFeature);

        // 2. Port Point Features
        for (Port stop : route.getStops()) {
            Map<String, Object> pointFeature = new LinkedHashMap<>();
            pointFeature.put("type", "Feature");

            Map<String, Object> pointGeometry = new LinkedHashMap<>();
            pointGeometry.put("type", "Point");
            pointGeometry.put("coordinates", Arrays.asList(stop.getLongitude(), stop.getLatitude()));
            pointFeature.put("geometry", pointGeometry);

            Map<String, Object> pointProperties = new LinkedHashMap<>();
            pointProperties.put("type", "portStop");
            pointProperties.put("stopNumber", stop.getStopNumber());
            pointProperties.put("portId", stop.getId());
            pointProperties.put("name", stop.getName());
            pointProperties.put("country", stop.getCountry());
            pointProperties.put("isOrigin", stop.getStopNumber() == 1);
            pointFeature.put("properties", pointProperties);

            features.add(pointFeature);
        }

        featureCollection.put("features", features);
        return featureCollection;
    }
}
