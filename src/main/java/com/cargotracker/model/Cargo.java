package com.cargotracker.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the cargo manifest of the ship.
 * The vessel carries 24,000 container boxes, which must be distributed
 * across all stops (including the start port if specified, and all destination ports).
 */
public class Cargo {

    public static final int TOTAL_CAPACITY = 24000;

    private String cargoType;
    private int totalBoxes;
    private int deliveredBoxes;
    private int remainingBoxes;
    private Map<String, Integer> allocations; // Map of portId -> boxes to unload

    public Cargo() {
        this.cargoType = "Standard TEU Containers";
        this.totalBoxes = TOTAL_CAPACITY;
        this.deliveredBoxes = 0;
        this.remainingBoxes = TOTAL_CAPACITY;
        this.allocations = new HashMap<>();
    }

    public Cargo(int totalBoxes) {
        this.cargoType = "Standard TEU Containers";
        this.totalBoxes = totalBoxes;
        this.deliveredBoxes = 0;
        this.remainingBoxes = totalBoxes;
        this.allocations = new HashMap<>();
    }

    public String getCargoType() {
        return cargoType;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    public int getTotalBoxes() {
        return totalBoxes;
    }

    public void setTotalBoxes(int totalBoxes) {
        this.totalBoxes = totalBoxes;
    }

    public int getDeliveredBoxes() {
        return deliveredBoxes;
    }

    public void setDeliveredBoxes(int deliveredBoxes) {
        this.deliveredBoxes = deliveredBoxes;
    }

    public int getRemainingBoxes() {
        return remainingBoxes;
    }

    public void setRemainingBoxes(int remainingBoxes) {
        this.remainingBoxes = remainingBoxes;
    }

    public Map<String, Integer> getAllocations() {
        return allocations;
    }

    public void setAllocations(Map<String, Integer> allocations) {
        this.allocations = allocations;
    }

    /**
     * Calculates the sum of all allocated boxes across all ports.
     */
    public int calculateTotalAllocated() {
        if (allocations == null) {
            return 0;
        }
        return allocations.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Checks if the total allocated boxes across all ports equals 24,000
     * and that no individual allocation is negative.
     */
    public boolean isValid() {
        if (allocations == null || allocations.isEmpty()) {
            return false;
        }
        boolean hasNegative = allocations.values().stream().anyMatch(val -> val == null || val < 0);
        if (hasNegative) {
            return false;
        }
        return calculateTotalAllocated() == totalBoxes;
    }
}
