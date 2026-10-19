package org.gomma.diff;

import org.gomma.diff.utils.DataBaseHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class JdbcDriverLoadingTest {
    @TempDir Path directory;

    @Test
    void opensDatabaseWhenJdbcDiscoveryRanWithoutTheLibraryClassLoader() throws Exception {
        Path log = directory.resolve("jdbc.log");
        Process process = new ProcessBuilder(
                Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), Probe.class.getName())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "JDBC probe timed out");
            assertEquals(0, process.exitValue(), new String(Files.readAllBytes(log), java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    public static class Probe {
        public static void main(String[] args) throws Exception {
            // JDBC discovery happens once. Simulate a host starting it before the
            // application's dependency class loader is available (e.g. a Boot JAR).
            ClassLoader applicationLoader = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(new ClassLoader(null) {});
                DriverManager.getDrivers();
            } finally {
                Thread.currentThread().setContextClassLoader(applicationLoader);
            }
            try (DataBaseHandler database = new DataBaseHandler()) {
                database.executeDml("CREATE TABLE driver_probe (id INT)");
                database.executeDml("INSERT INTO driver_probe VALUES (1)");
            }
        }
    }
}
