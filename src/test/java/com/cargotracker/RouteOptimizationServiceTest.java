package com.cargotracker;

import com.cargotracker.model.Port;
import com.cargotracker.model.Route;
import com.cargotracker.service.PortService;
import com.cargotracker.service.RouteOptimizationService;
import com.cargotracker.service.SeaRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RouteOptimizationServiceTest {

    private SeaRoutingService seaRoutingService;
    private RouteOptimizationService routeOptimizationService;
    private PortService portService;

    @BeforeEach
    public void setUp() {
        seaRoutingService = new SeaRoutingService();
        seaRoutingService.init();
        routeOptimizationService = new RouteOptimizationService(seaRoutingService);
        portService = new PortService();
        portService.init();
    }

    @Test
    public void testOptimizationWith3DestinationsAndPairCaching() {
        Port start = portService.findById("IN_BOM").orElseThrow();
        Port japan = portService.findById("JP_TYO").orElseThrow();
        Port indonesia = portService.findById("ID_JKT").orElseThrow();
        Port madagascar = portService.findById("MG_TOA").orElseThrow();

        List<Port> destinations = Arrays.asList(japan, indonesia, madagascar);

        int cacheBefore = seaRoutingService.getCacheSize();
        Route optimalRoute = routeOptimizationService.findShortestRoute(start, destinations);
        assertNotNull(optimalRoute);
        assertEquals(4, optimalRoute.getStops().size(), "Should have 4 stops (start + 3 destinations)");
        assertEquals("IN_BOM", optimalRoute.getStops().get(0).getId());
        assertTrue(optimalRoute.getTotalDistanceKm() > 0);

        // Verify pair cache was populated and reused
        int cacheAfter = seaRoutingService.getCacheSize();
        assertTrue(cacheAfter > cacheBefore, "Pair cache should have been populated");

        // Run calculation again with same ports: cache size should not increase!
        routeOptimizationService.findShortestRoute(start, destinations);
        assertEquals(cacheAfter, seaRoutingService.getCacheSize(), "Cache size should remain identical on second run");
    }
}
