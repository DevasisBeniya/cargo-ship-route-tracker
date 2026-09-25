package com.cargotracker.service;

import com.cargotracker.model.Cargo;
import com.cargotracker.model.Port;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for cargo allocation calculations and validation.
 *
 * Enforces business invariants:
 * 1. Ship initial load is configurable by the user (defaulting to 24,000 TEU boxes).
 * 2. Unloading can occur at ANY stop, INCLUDING the origin/start port (e.g. India).
 * 3. The sum of all unloaded boxes across ALL stops (start port + destination ports)
 *    must equal exactly the ship's load.
 * 4. Computes the sequential cargo balance remaining on the ship after each port stop.
 */
@Service
public class CargoService {

    public static final int DEFAULT_CAPACITY = 24000;

    /**
     * Validates cargo allocations across all candidate ports.
     *
     * @param allPortIds list of all port IDs in the planned itinerary (start + destinations)
     * @param allocations map of portId -> boxes to unload
     * @param shipCapacity total ship capacity (default 24,000)
     * @return list of validation error strings, empty if valid
     */
    public List<String> validateAllocations(List<String> allPortIds, Map<String, Integer> allocations, int shipCapacity) {
        List<String> errors = new ArrayList<>();
        int requiredTotal = shipCapacity > 0 ? shipCapacity : DEFAULT_CAPACITY;

        if (allocations == null || allocations.isEmpty()) {
            errors.add(String.format("No cargo allocations provided. Exactly %d boxes must be distributed across all stops.", requiredTotal));
            return errors;
        }

        int totalAllocated = 0;
        for (String portId : allPortIds) {
            Integer count = allocations.get(portId);
            if (count == null) {
                count = 0;
            }
            if (count < 0) {
                errors.add("Boxes to unload at port " + portId + " cannot be negative: " + count);
            }
            totalAllocated += count;
        }

        if (totalAllocated != requiredTotal) {
            errors.add(String.format(
                    "Total boxes to unload across ALL stops (including start port) is %d, but must equal the ship load (%d). Difference: %d boxes.",
                    totalAllocated,
                    requiredTotal,
                    requiredTotal - totalAllocated
            ));
        }

        return errors;
    }

    /**
     * Applies cargo unloading to each stop along the calculated route in order,
     * computing the exact boxes unloaded and remaining boxes after departure.
     *
     * @param orderedStops ports ordered in visiting sequence
     * @param allocations map of portId -> boxes to unload
     * @param shipCapacity total ship capacity (default 24,000)
     * @return updated Cargo summary
     */
    public Cargo applyCargoToStops(List<Port> orderedStops, Map<String, Integer> allocations, int shipCapacity) {
        int capacity = shipCapacity > 0 ? shipCapacity : DEFAULT_CAPACITY;
        Cargo cargo = new Cargo(capacity);
        cargo.setAllocations(allocations);

        int currentShipLoad = capacity;

        for (int i = 0; i < orderedStops.size(); i++) {
            Port stop = orderedStops.get(i);
            int unloadCount = allocations != null && allocations.containsKey(stop.getId())
                    ? allocations.get(stop.getId())
                    : 0;

            stop.setBoxesToUnload(unloadCount);
            currentShipLoad -= unloadCount;
            stop.setBoxesRemainingAfterUnload(currentShipLoad);

            if (i == 0) {
                // First stop (Start Port)
                stop.setStatus(unloadCount > 0 ? "Origin (Partial Unload)" : "Origin (Loaded)");
            } else {
                stop.setStatus("Scheduled");
            }
        }

        cargo.setDeliveredBoxes(0);
        cargo.setRemainingBoxes(capacity);

        return cargo;
    }
}
