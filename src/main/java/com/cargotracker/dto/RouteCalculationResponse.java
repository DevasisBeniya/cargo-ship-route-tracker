package com.cargotracker.dto;

import com.cargotracker.model.Cargo;
import com.cargotracker.model.Port;
import com.cargotracker.model.Route;
import com.cargotracker.model.Ship;

import java.util.ArrayList;
import java.util.List;

/**
 * Response payload returned to the frontend containing the optimized route,
 * vessel details, cargo breakdown, stop schedule, and any validation messages.
 */
public class RouteCalculationResponse {

    private boolean success;
    private String message;
    private Route route;
    private Ship ship;
    private Cargo cargo;
    private List<Port> stops;
    private List<String> errors;

    public RouteCalculationResponse() {
        this.errors = new ArrayList<>();
    }

    public static RouteCalculationResponse error(String message) {
        RouteCalculationResponse res = new RouteCalculationResponse();
        res.setSuccess(false);
        res.setMessage(message);
        res.getErrors().add(message);
        return res;
    }

    public static RouteCalculationResponse error(List<String> errors) {
        RouteCalculationResponse res = new RouteCalculationResponse();
        res.setSuccess(false);
        res.setMessage("Validation failed");
        res.setErrors(errors);
        return res;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public Ship getShip() {
        return ship;
    }

    public void setShip(Ship ship) {
        this.ship = ship;
    }

    public Cargo getCargo() {
        return cargo;
    }

    public void setCargo(Cargo cargo) {
        this.cargo = cargo;
    }

    public List<Port> getStops() {
        return stops;
    }

    public void setStops(List<Port> stops) {
        this.stops = stops;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
