package com.cargotracker.service;

import com.cargotracker.model.Port;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing global seaports loaded from 'src/main/resources/ports.json',
 * with support for dynamically registering custom ports created at runtime by the user.
 */
@Service
public class PortService {

    private static final Logger log = LoggerFactory.getLogger(PortService.class);
    private final Map<String, Port> portCatalog = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        loadPortsFromJson();
    }

    /**
     * Loads the master catalogue of international ports from ports.json.
     */
    public synchronized void loadPortsFromJson() {
        try {
            ClassPathResource resource = new ClassPathResource("ports.json");
            try (InputStream is = resource.getInputStream()) {
                List<Port> ports = objectMapper.readValue(is, new TypeReference<List<Port>>() {});
                for (Port port : ports) {
                    portCatalog.put(port.getId(), port);
                }
                log.info("Loaded {} international seaports from ports.json", portCatalog.size());
            }
        } catch (Exception e) {
            log.error("Failed to load ports from ports.json. Falling back to default baseline ports.", e);
            populateFallbackPorts();
        }
    }

    private void populateFallbackPorts() {
        register(new Port("IN_BOM", "Port of Mumbai / JNPT", "India", 18.9499, 72.9510));
        register(new Port("JP_TYO", "Port of Tokyo", "Japan", 35.6197, 139.7915));
        register(new Port("ID_JKT", "Port of Tanjung Priok (Jakarta)", "Indonesia", -6.1030, 106.8834));
        register(new Port("MG_TOA", "Port of Toamasina", "Madagascar", -18.1500, 49.4167));
        register(new Port("SG_SIN", "Port of Singapore", "Singapore", 1.2644, 103.8220));
    }

    private void register(Port port) {
        portCatalog.put(port.getId(), port);
    }

    /**
     * Adds a custom user-defined port created at runtime via clicking on the map or manual entry.
     *
     * @param customPort user submitted port
     * @return registered port with validated ID and coordinates
     */
    public Port addCustomPort(Port customPort) {
        if (customPort.getName() == null || customPort.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Port name is required.");
        }
        if (customPort.getCountry() == null || customPort.getCountry().trim().isEmpty()) {
            customPort.setCountry("Custom");
        }
        if (customPort.getLatitude() < -90.0 || customPort.getLatitude() > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90.0 and 90.0.");
        }
        if (customPort.getLongitude() < -180.0 || customPort.getLongitude() > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180.0 and 180.0.");
        }

        String id = customPort.getId();
        if (id == null || id.trim().isEmpty()) {
            id = "CUST_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            customPort.setId(id);
        }

        customPort.setStatus("Custom Port");
        portCatalog.put(customPort.getId(), customPort);
        log.info("Registered custom port: {} ({}, {})", customPort.getName(), customPort.getLatitude(), customPort.getLongitude());
        return customPort;
    }

    /**
     * Returns all available ports sorted alphabetically by Country, then Name.
     */
    public List<Port> getAllPorts() {
        List<Port> list = new ArrayList<>(portCatalog.values());
        list.sort(Comparator.comparing(Port::getCountry, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Port::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public Optional<Port> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(portCatalog.get(id));
    }
}
