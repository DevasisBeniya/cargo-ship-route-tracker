package com.cargotracker.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents the complete optimized sea voyage route.
 * Contains ordered port stops, voyage segments, total distance,
 * and a full GeoJSON representation ready for Leaflet.js mapping.
 */
public class Route {

    private List<Port> stops;
    private double totalDistanceKm;
    private double totalDistanceNm;
    private List<RouteSegment> segments;
    private Map<String, Object> geoJson;
    private String routingEngine;

    public Route() {
        this.stops = new ArrayList<>();
        this.segments = new ArrayList<>();
    }

    public List<Port> getStops() {
        return stops;
    }

    public void setStops(List<Port> stops) {
        this.stops = stops;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public void setTotalDistanceKm(double totalDistanceKm) {
        this.totalDistanceKm = totalDistanceKm;
    }

    public double getTotalDistanceNm() {
        return totalDistanceNm;
    }

    public void setTotalDistanceNm(double totalDistanceNm) {
        this.totalDistanceNm = totalDistanceNm;
    }

    public List<RouteSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<RouteSegment> segments) {
        this.segments = segments;
    }

    public Map<String, Object> getGeoJson() {
        return geoJson;
    }

    public void setGeoJson(Map<String, Object> geoJson) {
        this.geoJson = geoJson;
    }

    public String getRoutingEngine() {
        return routingEngine;
    }

    public void setRoutingEngine(String routingEngine) {
        this.routingEngine = routingEngine;
    }
}
