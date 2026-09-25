package com.cargotracker.dto;

import java.util.List;
import java.util.Map;

/**
 * Request payload submitted by the user from the web frontend.
 */
public class RouteCalculationRequest {

    private String startPortId;
    private List<String> destinationPortIds;
    private int shipCapacity = 24000; // Default capacity: 24,000 boxes
    // Map of portId -> boxes to unload at that port (including startPortId and each destinationPortId)
    private Map<String, Integer> cargoAllocations;

    public RouteCalculationRequest() {
    }

    public RouteCalculationRequest(String startPortId, List<String> destinationPortIds, int shipCapacity, Map<String, Integer> cargoAllocations) {
        this.startPortId = startPortId;
        this.destinationPortIds = destinationPortIds;
        this.shipCapacity = shipCapacity > 0 ? shipCapacity : 24000;
        this.cargoAllocations = cargoAllocations;
    }

    public String getStartPortId() {
        return startPortId;
    }

    public void setStartPortId(String startPortId) {
        this.startPortId = startPortId;
    }

    public List<String> getDestinationPortIds() {
        return destinationPortIds;
    }

    public void setDestinationPortIds(List<String> destinationPortIds) {
        this.destinationPortIds = destinationPortIds;
    }

    public int getShipCapacity() {
        return shipCapacity;
    }

    public void setShipCapacity(int shipCapacity) {
        this.shipCapacity = shipCapacity > 0 ? shipCapacity : 24000;
    }

    public Map<String, Integer> getCargoAllocations() {
        return cargoAllocations;
    }

    public void setCargoAllocations(Map<String, Integer> cargoAllocations) {
        this.cargoAllocations = cargoAllocations;
    }
}
