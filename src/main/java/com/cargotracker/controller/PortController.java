package com.cargotracker.controller;

import com.cargotracker.model.Port;
import com.cargotracker.service.PortService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller providing port endpoints:
 * - GET /api/ports : Returns catalogue of all ports
 * - POST /api/ports : Registers a custom port created by user interaction
 */
@RestController
@RequestMapping("/api/ports")
public class PortController {

    private final PortService portService;

    public PortController(PortService portService) {
        this.portService = portService;
    }

    @GetMapping
    public ResponseEntity<List<Port>> getAllPorts() {
        return ResponseEntity.ok(portService.getAllPorts());
    }

    @PostMapping
    public ResponseEntity<?> addCustomPort(@RequestBody Port customPort) {
        try {
            Port savedPort = portService.addCustomPort(customPort);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("port", savedPort);
            response.put("message", "Custom port '" + savedPort.getName() + "' successfully registered.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
