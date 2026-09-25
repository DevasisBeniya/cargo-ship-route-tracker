package com.cargotracker.model;

/**
 * Represents the cargo vessel carrying container boxes across international ports.
 * The vessel has a fixed capacity of 24,000 standard TEU container boxes.
 */
public class Ship {

    public static final int DEFAULT_CAPACITY = 24000;
    public static final String SHIP_NAME = "MV Pirates Voyager";

    private String name;
    private int capacity;
    private int currentLoad;
    private int deliveredLoad;
    private String currentPort;
    private String status; // "Docked at Origin", "Underway", "Unloading", "Voyage Completed"

    public Ship() {
        this.name = SHIP_NAME;
        this.capacity = DEFAULT_CAPACITY;
        this.currentLoad = DEFAULT_CAPACITY;
        this.deliveredLoad = 0;
        this.status = "Docked at Origin";
    }

    public Ship(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
        this.currentLoad = capacity;
        this.deliveredLoad = 0;
        this.status = "Docked at Origin";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getCurrentLoad() {
        return currentLoad;
    }

    public void setCurrentLoad(int currentLoad) {
        this.currentLoad = currentLoad;
    }

    public int getDeliveredLoad() {
        return deliveredLoad;
    }

    public void setDeliveredLoad(int deliveredLoad) {
        this.deliveredLoad = deliveredLoad;
    }

    public String getCurrentPort() {
        return currentPort;
    }

    public void setCurrentPort(String currentPort) {
        this.currentPort = currentPort;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Records cargo unloading at a destination or start port stop.
     * Decreases current load and increases delivered load.
     *
     * @param boxes amount of boxes unloaded
     */
    public void unload(int boxes) {
        if (boxes < 0 || boxes > this.currentLoad) {
            throw new IllegalArgumentException("Cannot unload " + boxes + " boxes. Current load is " + this.currentLoad);
        }
        this.currentLoad -= boxes;
        this.deliveredLoad += boxes;
    }
}
