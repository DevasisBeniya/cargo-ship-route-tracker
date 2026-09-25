package com.cargotracker.model;

/**
 * Represents a commercial sea port with its geographic coordinates
 * and voyage stop schedule information.
 *
 * Designed for educational clarity: explains both the geospatial location
 * of the maritime port and its stop status along the voyage route.
 */
public class Port {

    private String id;
    private String name;
    private String country;
    private double latitude;
    private double longitude;

    // Voyage Stop Information (populated during route planning and execution)
    private int stopNumber;                      // Sequence number (1 for start port, 2 for first stop, etc.)
    private int boxesToUnload;                   // Number of container boxes to unload at this port
    private int boxesRemainingAfterUnload;       // Cargo boxes remaining on the ship after leaving this port
    private String status;                       // Status: "Origin", "Scheduled", "Unloading", "Delivered"

    public Port() {
    }

    public Port(String id, String name, String country, double latitude, double longitude) {
        this.id = id;
        this.name = name;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = "Scheduled";
    }

    // Copy constructor to clone a Port prototype for an active voyage stop
    public Port(Port other) {
        this.id = other.id;
        this.name = other.name;
        this.country = other.country;
        this.latitude = other.latitude;
        this.longitude = other.longitude;
        this.stopNumber = other.stopNumber;
        this.boxesToUnload = other.boxesToUnload;
        this.boxesRemainingAfterUnload = other.boxesRemainingAfterUnload;
        this.status = other.status != null ? other.status : "Scheduled";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getStopNumber() {
        return stopNumber;
    }

    public void setStopNumber(int stopNumber) {
        this.stopNumber = stopNumber;
    }

    public int getBoxesToUnload() {
        return boxesToUnload;
    }

    public void setBoxesToUnload(int boxesToUnload) {
        this.boxesToUnload = boxesToUnload;
    }

    public int getBoxesRemainingAfterUnload() {
        return boxesRemainingAfterUnload;
    }

    public void setBoxesRemainingAfterUnload(int boxesRemainingAfterUnload) {
        this.boxesRemainingAfterUnload = boxesRemainingAfterUnload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Port{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", country='" + country + '\'' +
                ", stopNumber=" + stopNumber +
                ", boxesToUnload=" + boxesToUnload +
                ", boxesRemainingAfterUnload=" + boxesRemainingAfterUnload +
                '}';
    }
}
