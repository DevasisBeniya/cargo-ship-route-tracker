package com.cargotracker;

import com.cargotracker.model.Cargo;
import com.cargotracker.model.Port;
import com.cargotracker.service.CargoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CargoServiceTest {

    private CargoService cargoService;

    @BeforeEach
    public void setUp() {
        cargoService = new CargoService();
    }

    @Test
    public void testValidAllocationsAcrossAllStops() {
        List<String> portIds = Arrays.asList("IN_BOM", "JP_TYO", "ID_JKT", "MG_TOA");
        Map<String, Integer> allocations = new HashMap<>();
        allocations.put("IN_BOM", 4000);  // Start port unloads 4,000
        allocations.put("JP_TYO", 8000);  // Japan unloads 8,000
        allocations.put("ID_JKT", 7000);  // Indonesia unloads 7,000
        allocations.put("MG_TOA", 5000);  // Madagascar unloads 5,000
        // Total = 4000 + 8000 + 7000 + 5000 = 24000

        List<String> errors = cargoService.validateAllocations(portIds, allocations, 24000);
        assertTrue(errors.isEmpty(), "Should have no errors when total equals 24,000");

        // Test applying to ordered stops
        List<Port> stops = new ArrayList<>();
        stops.add(new Port("IN_BOM", "Port of Mumbai", "India", 18.9, 72.9));
        stops.add(new Port("JP_TYO", "Port of Tokyo", "Japan", 35.6, 139.7));
        stops.add(new Port("ID_JKT", "Port of Jakarta", "Indonesia", -6.1, 106.8));
        stops.add(new Port("MG_TOA", "Port of Toamasina", "Madagascar", -18.1, 49.4));

        Cargo manifest = cargoService.applyCargoToStops(stops, allocations, 24000);
        assertNotNull(manifest);

        // Verify Stop 1 (Start Port)
        assertEquals(4000, stops.get(0).getBoxesToUnload());
        assertEquals(20000, stops.get(0).getBoxesRemainingAfterUnload());

        // Verify Stop 2
        assertEquals(8000, stops.get(1).getBoxesToUnload());
        assertEquals(12000, stops.get(1).getBoxesRemainingAfterUnload());

        // Verify Stop 3
        assertEquals(7000, stops.get(2).getBoxesToUnload());
        assertEquals(5000, stops.get(2).getBoxesRemainingAfterUnload());

        // Verify Stop 4 (Final stop: all cargo unloaded)
        assertEquals(5000, stops.get(3).getBoxesToUnload());
        assertEquals(0, stops.get(3).getBoxesRemainingAfterUnload());
    }

    @Test
    public void testInvalidTotalAllocationTriggersError() {
        List<String> portIds = Arrays.asList("IN_BOM", "JP_TYO", "ID_JKT");
        Map<String, Integer> allocations = new HashMap<>();
        allocations.put("IN_BOM", 5000);
        allocations.put("JP_TYO", 10000);
        allocations.put("ID_JKT", 6000);
        // Total = 21,000 (3,000 short of 24,000)

        List<String> errors = cargoService.validateAllocations(portIds, allocations, 24000);
        assertFalse(errors.isEmpty(), "Should report validation error when total does not match 24,000");
        assertTrue(errors.get(0).contains("must equal the ship load (24000)"));
    }
}
