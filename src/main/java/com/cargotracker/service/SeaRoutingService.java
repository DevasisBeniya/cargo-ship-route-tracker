package com.cargotracker.service;

import com.cargotracker.model.Port;
import com.cargotracker.model.RouteSegment;
import eu.europa.ec.eurostat.jgiscotools.feature.Feature;
import eu.europa.ec.eurostat.jgiscotools.util.GeoDistanceUtil;
import eu.europa.ec.eurostat.searoute.SeaRouting;
import jakarta.annotation.PostConstruct;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Service managing Eurostat SeaRoute calculations.
 *
 * Core capabilities:
 * 1. Automatically extracts marnet GeoPackage files from the Eurostat searoute-core jar
 *    to the local filesystem so the SQLite GeoPackage engine can access them.
 * 2. Caches every port pair's sea distance and polyline so each pair is calculated
 *    only once and reused symmetrically (distance A->B equals B->A).
 * 3. Gracefully detects when coordinates cannot be connected to maritime shipping lanes
 *    (e.g., landlocked or unreachable ports) and reports a user-friendly error.
 */
@Service
public class SeaRoutingService {

    private static final Logger log = LoggerFactory.getLogger(SeaRoutingService.class);

    private SeaRouting seaRouting;

    // Cache holding computed sea legs for undirected port pairs
    // Key format: smallerPortId + "<->" + largerPortId
    private final Map<String, CachedSeaLeg> pairCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            ensureMarnetFilesExtracted();
            // Initialize SeaRouting with 20km maritime network resolution
            log.info("Initializing Eurostat SeaRouting engine (20km resolution)...");
            this.seaRouting = new SeaRouting(20);
            log.info("Eurostat SeaRouting engine successfully initialized.");
        } catch (Exception e) {
            log.error("Failed to initialize Eurostat SeaRouting engine: {}", e.getMessage(), e);
            throw new IllegalStateException("Eurostat SeaRoute initialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts marnet_plus_*.gpkg files from searoute-core jar into the local ./marnet folder
     * if they do not already exist on disk.
     */
    private void ensureMarnetFilesExtracted() {
        File marnetDir = new File("marnet");
        if (!marnetDir.exists()) {
            marnetDir.mkdirs();
        }

        File targetGpkg = new File("marnet/marnet_plus_20km.gpkg");
        if (targetGpkg.exists() && targetGpkg.length() > 0) {
            log.info("Maritime network files already present in {}", marnetDir.getAbsolutePath());
            return;
        }

        try {
            Class<?> cls = SeaRouting.class;
            URL location = cls.getProtectionDomain().getCodeSource().getLocation();
            File jarFile = new File(location.toURI());

            if (jarFile.isFile() && jarFile.getName().endsWith(".jar")) {
                try (JarFile jf = new JarFile(jarFile)) {
                    Enumeration<JarEntry> entries = jf.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (entry.getName().startsWith("marnet/") && entry.getName().endsWith(".gpkg")) {
                            File dest = new File(entry.getName());
                            if (!dest.exists() || dest.length() == 0) {
                                try (InputStream is = jf.getInputStream(entry);
                                     FileOutputStream fos = new FileOutputStream(dest)) {
                                    is.transferTo(fos);
                                }
                                log.info("Extracted maritime network dataset: {} ({} bytes)", dest.getName(), dest.length());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not automatically extract marnet files from jar: {}", e.getMessage());
        }
    }

    /**
     * Computes or retrieves from cache the maritime leg between two ports.
     * Caching ensures each port pair's sea distance is calculated only once.
     *
     * @param origin departure port
     * @param destination arrival port
     * @return RouteSegment with distance and coordinates
     */
    public RouteSegment calculateLeg(Port origin, Port destination) {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("Origin and destination ports cannot be null.");
        }
        if (origin.getId().equals(destination.getId())) {
            return new RouteSegment(origin, destination, 0.0, 0.0, Collections.emptyList());
        }

        String originId = origin.getId();
        String destId = destination.getId();
        boolean isForward = originId.compareTo(destId) < 0;
        String pairKey = isForward ? (originId + "<->" + destId) : (destId + "<->" + originId);

        // Check pair cache
        CachedSeaLeg cached = pairCache.get(pairKey);
        if (cached != null) {
            log.debug("Cache hit for port pair {}", pairKey);
            List<List<Double>> coords = isForward ? cached.coordinates : getReversedCoordinates(cached.coordinates);
            return new RouteSegment(origin, destination, cached.distanceKm, cached.distanceNm, coords);
        }

        // Cache miss: compute sea route using Eurostat SeaRoute library
        log.info("Computing maritime route for port pair {} ({} -> {})", pairKey, origin.getName(), destination.getName());
        Port portA = isForward ? origin : destination;
        Port portB = isForward ? destination : origin;

        try {
            Feature feature = seaRouting.getRoute(
                    portA.getLongitude(), portA.getLatitude(),
                    portB.getLongitude(), portB.getLatitude()
            );

            if (feature == null || feature.getGeometry() == null) {
                throw new IllegalStateException(String.format(
                        "No maritime route could be established between '%s' and '%s'. One or both ports may be too far inland or disconnected from open sea lanes.",
                        portA.getName(), portB.getName()
                ));
            }

            Geometry geom = feature.getGeometry();
            double distanceKm = GeoDistanceUtil.getLengthGeoKM(geom);

            if (distanceKm <= 0.0) {
                throw new IllegalStateException(String.format(
                        "Calculated distance between '%s' and '%s' is zero or invalid. Check port coordinates.",
                        portA.getName(), portB.getName()
                ));
            }

            double distanceNm = distanceKm * 0.539957; // Conversion from km to nautical miles

            // The SeaRoute library does not promise in which direction the line runs (it may come
            // back from port B to port A, or in several pieces). Put the points in order from
            // portA to portB before caching them, so every leg starts at its own origin port.
            List<List<Double>> canonicalCoords = extractOrderedCoordinates(geom, portA, portB);

            // Store in undirected pair cache
            CachedSeaLeg newLeg = new CachedSeaLeg(distanceKm, distanceNm, canonicalCoords);
            pairCache.put(pairKey, newLeg);

            List<List<Double>> directedCoords = isForward ? canonicalCoords : getReversedCoordinates(canonicalCoords);
            return new RouteSegment(origin, destination, distanceKm, distanceNm, directedCoords);

        } catch (Exception e) {
            log.error("Eurostat SeaRoute calculation failed for {} -> {}: {}", portA.getName(), portB.getName(), e.getMessage());
            throw new IllegalStateException(String.format(
                    "Maritime route calculation failed between '%s' and '%s': %s",
                    origin.getName(), destination.getName(), e.getMessage()
            ), e);
        }
    }

    /**
     * Reads the route geometry (one or more line pieces) and returns one continuous list of
     * [longitude, latitude] points that starts at 'from' and ends at 'to'.
     */
    private List<List<Double>> extractOrderedCoordinates(Geometry geom, Port from, Port to) {
        List<List<double[]>> parts = new ArrayList<>();
        for (int i = 0; i < geom.getNumGeometries(); i++) {
            Coordinate[] coordinates = geom.getGeometryN(i).getCoordinates();
            List<double[]> part = new ArrayList<>();
            for (Coordinate c : coordinates) {
                part.add(new double[]{c.x, c.y}); // [longitude, latitude] for GeoJSON
            }
            parts.add(part);
        }
        return LegGeometryHelper.orderRoute(parts,
                from.getLongitude(), from.getLatitude(),
                to.getLongitude(), to.getLatitude());
    }

    private List<List<Double>> getReversedCoordinates(List<List<Double>> original) {
        List<List<Double>> reversed = new ArrayList<>(original);
        Collections.reverse(reversed);
        return reversed;
    }

    /**
     * Clears the in-memory pair cache if needed (e.g. for testing).
     */
    public void clearCache() {
        pairCache.clear();
    }

    public int getCacheSize() {
        return pairCache.size();
    }

    /**
     * Internal container for cached symmetric sea leg data.
     */
    private static class CachedSeaLeg {
        final double distanceKm;
        final double distanceNm;
        final List<List<Double>> coordinates;

        CachedSeaLeg(double distanceKm, double distanceNm, List<List<Double>> coordinates) {
            this.distanceKm = distanceKm;
            this.distanceNm = distanceNm;
            this.coordinates = coordinates;
        }
    }

    /**
     * Puts the points of a sea route into the correct order.
     *
     * Why this is needed (for the project report / viva):
     * The Eurostat SeaRoute library builds a route from many small pieces of the maritime network
     * and joins them with the JTS LineMerger. LineMerger does not promise in which direction the
     * finished line runs, so a route from Mumbai to Madagascar can come back as Madagascar to Mumbai,
     * or in several pieces. The map and the ship animation expect every leg to start at its origin
     * port and end at its destination port, so the order is fixed here before the route is used.
     */
    private static final class LegGeometryHelper {

        private LegGeometryHelper() {
        }

        /**
         * @param parts   the pieces of the route; each piece is a list of {longitude, latitude}
         * @param fromLon longitude of the origin port
         * @param fromLat latitude of the origin port
         * @param toLon   longitude of the destination port
         * @param toLat   latitude of the destination port
         * @return one continuous list of [longitude, latitude] points that starts at the origin
         *         and ends at the destination
         */
        static List<List<Double>> orderRoute(List<List<double[]>> parts,
                                             double fromLon, double fromLat,
                                             double toLon, double toLat) {
            double[] from = {fromLon, fromLat};
            double[] to = {toLon, toLat};

            // Keep only pieces that really contain points (work on copies)
            List<List<double[]>> remaining = new ArrayList<>();
            for (List<double[]> part : parts) {
                if (part != null && !part.isEmpty()) {
                    remaining.add(new ArrayList<>(part));
                }
            }
            if (remaining.isEmpty()) {
                return new ArrayList<>();
            }

            // Step 1: join the pieces into one continuous line, starting at the origin side
            List<double[]> chain = new ArrayList<>(takeClosestPiece(remaining, from));
            while (!remaining.isEmpty()) {
                chain.addAll(takeClosestPiece(remaining, chain.get(chain.size() - 1)));
            }

            // Step 2: make sure the whole line runs from the origin to the destination
            double forwardGap = distance(chain.get(0), from) + distance(chain.get(chain.size() - 1), to);
            double backwardGap = distance(chain.get(chain.size() - 1), from) + distance(chain.get(0), to);
            if (backwardGap < forwardGap) {
                Collections.reverse(chain);
            }

            List<List<Double>> result = new ArrayList<>(chain.size());
            for (double[] p : chain) {
                result.add(Arrays.asList(p[0], p[1]));
            }
            return result;
        }

        /**
         * Removes from 'pieces' the piece that has an end closest to 'point' and returns it,
         * turned around if needed so that the end closest to 'point' comes first.
         */
        private static List<double[]> takeClosestPiece(List<List<double[]>> pieces, double[] point) {
            int bestIndex = 0;
            boolean bestReversed = false;
            double bestDistance = Double.MAX_VALUE;

            for (int i = 0; i < pieces.size(); i++) {
                List<double[]> piece = pieces.get(i);
                double distFirst = distance(piece.get(0), point);
                double distLast = distance(piece.get(piece.size() - 1), point);
                if (distFirst < bestDistance) {
                    bestDistance = distFirst;
                    bestIndex = i;
                    bestReversed = false;
                }
                if (distLast < bestDistance) {
                    bestDistance = distLast;
                    bestIndex = i;
                    bestReversed = true;
                }
            }

            List<double[]> chosen = pieces.remove(bestIndex);
            if (bestReversed) {
                Collections.reverse(chosen);
            }
            return chosen;
        }

        /**
         * Approximate distance in degrees between two {longitude, latitude} points.
         * It only has to tell "closer" from "farther", so a simple formula is enough.
         */
        private static double distance(double[] a, double[] b) {
            double dLon = Math.abs(a[0] - b[0]);
            if (dLon > 180) {
                dLon = 360 - dLon;   // shortest way round the globe
            }
            double dLat = a[1] - b[1];
            double meanLat = Math.toRadians((a[1] + b[1]) / 2);
            return Math.hypot(dLon * Math.cos(meanLat), dLat);
        }
    }
}
