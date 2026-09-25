package com.cargotracker.model;

import java.util.List;

/**
 * Represents a single navigational leg between two consecutive ports on the sea voyage.
 */
public class RouteSegment {

    private Port origin;
    private Port destination;
    private double distanceKm;
    private double distanceNm;
    // List of coordinates [longitude, latitude] defining the maritime polyline
    private List<List<Double>> coordinates;

    public RouteSegment() {
    }

    public RouteSegment(Port origin, Port destination, double distanceKm, double distanceNm, List<List<Double>> coordinates) {
        this.origin = origin;
        this.destination = destination;
        this.distanceKm = distanceKm;
        this.distanceNm = distanceNm;
        this.coordinates = coordinates;
    }

    public Port getOrigin() {
        return origin;
    }

    public void setOrigin(Port origin) {
        this.origin = origin;
    }

    public Port getDestination() {
        return destination;
    }

    public void setDestination(Port destination) {
        this.destination = destination;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public double getDistanceNm() {
        return distanceNm;
    }

    public void setDistanceNm(double distanceNm) {
        this.distanceNm = distanceNm;
    }

    public List<List<Double>> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<List<Double>> coordinates) {
        this.coordinates = coordinates;
    }
}
