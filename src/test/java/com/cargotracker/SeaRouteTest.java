package com.cargotracker;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

public class SeaRouteTest {

    @Test
    public void testExtractionAndSeaRouting() {
        try {
            // Extract marnet files from jar to local ./marnet directory
            File marnetDir = new File("marnet");
            if (!marnetDir.exists()) {
                marnetDir.mkdirs();
            }

            File jar = new File("C:/Users/DEVASIS/.m2/repository/eu/europa/ec/eurostat/searoute-core/3.6/searoute-core-3.6.jar");
            try (JarFile jf = new JarFile(jar)) {
                var entries = jf.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().startsWith("marnet/") && entry.getName().endsWith(".gpkg")) {
                        File dest = new File(entry.getName());
                        if (!dest.exists()) {
                            try (InputStream is = jf.getInputStream(entry);
                                 FileOutputStream fos = new FileOutputStream(dest)) {
                                is.transferTo(fos);
                            }
                            System.out.println("Extracted " + entry.getName() + " (" + dest.length() + " bytes)");
                        }
                    }
                }
            }

            // Now test SeaRouting(20)
            Class<?> seaRoutingClass = Class.forName("eu.europa.ec.eurostat.searoute.SeaRouting");
            System.out.println("Instantiating SeaRouting(20)...");
            Object sr = seaRoutingClass.getDeclaredConstructor(int.class).newInstance(20);
            assertNotNull(sr, "SeaRouting instance should not be null");
            System.out.println("Successfully created SeaRouting instance!");

            // Test getRoute method: Mumbai (72.9510, 18.9499) to Tokyo (139.7915, 35.6197)
            Method getRouteMethod = seaRoutingClass.getMethod("getRoute", double.class, double.class, double.class, double.class);
            Object feature = getRouteMethod.invoke(sr, 72.9510, 18.9499, 139.7915, 35.6197);
            System.out.println("Route feature result: " + feature);
            assertNotNull(feature, "Route feature should not be null");

            // Extract geometry
            Method getGeomMethod = feature.getClass().getMethod("getGeometry");
            Object geom = getGeomMethod.invoke(feature);
            System.out.println("Geometry type: " + (geom != null ? geom.getClass().getName() : "null"));

            // Calculate distance via GeoDistanceUtil
            Class<?> distUtilClass = Class.forName("eu.europa.ec.eurostat.jgiscotools.util.GeoDistanceUtil");
            Method getLengthMethod = distUtilClass.getMethod("getLengthGeoKM", Class.forName("org.locationtech.jts.geom.Geometry"));
            double km = (double) getLengthMethod.invoke(null, geom);
            System.out.println("Calculated Sea Distance Mumbai -> Tokyo: " + km + " km (" + (km * 0.539957) + " NM)");
            assertTrue(km > 5000, "Distance between Mumbai and Tokyo should be > 5000 km");

        } catch (Throwable t) {
            t.printStackTrace();
            fail("Failed: " + t);
        }
    }
}
