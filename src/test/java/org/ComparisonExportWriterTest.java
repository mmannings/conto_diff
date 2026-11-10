package org;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.webdifftool.client.model.SemanticDiff;
import static org.junit.jupiter.api.Assertions.*;

class ComparisonExportWriterTest {
    @TempDir Path directory;

    @Test void matchesLegacyBytesIncludingOrderingUnicodeAndEmptyOutput() throws Exception {
        SemanticDiff diff = new SemanticDiff();
        diff.setLocations(new LinkedHashMap<>());
        diff.getLocations().put("z", "first ä 😀\n");
        diff.getLocations().put("a", "second \\\"\n");
        diff.getActivities().put("activity", "activity\r\n");
        diff.getSourceEntities().put("source", "source");
        diff.getSdiffEntities().put("comparison", "comparison");
        diff.setBaseEntity("base");
        diff.setSoftwareAgent("agent");
        char[] chars = new char[8191]; Arrays.fill(chars, 'x');
        diff.getProvDMs().put("graph", new String(chars) + "😀\nunpaired:\uD800");
        verifyLegacy(diff);
        verifyLegacy(new SemanticDiff());
    }

    private void verifyLegacy(SemanticDiff diff) throws Exception {
        String nl = System.lineSeparator();
        StringBuilder metadata = new StringBuilder("prefixes" + nl + "header");
        metadata.append(diff.getBaseEntity()).append(nl).append(nl);
        diff.getLocations().values().forEach(metadata::append);
        diff.getActivities().values().forEach(metadata::append);
        diff.getSourceEntities().values().forEach(metadata::append);
        diff.getSdiffEntities().values().forEach(v -> metadata.append(v).append(nl));
        metadata.append(diff.getSoftwareAgent());
        StringBuilder changes = new StringBuilder();
        diff.getProvDMs().values().forEach(changes::append);
        Path ttl = directory.resolve("output.ttl"), nq = directory.resolve("changes.nq");
        ComparisonExportWriter.write(diff, "prefixes", "header", ttl, nq);
        assertArrayEquals(metadata.toString().getBytes(StandardCharsets.UTF_8), Files.readAllBytes(ttl));
        assertArrayEquals(changes.toString().getBytes(StandardCharsets.UTF_8), Files.readAllBytes(nq));
    }

    @Test void cancelledExportDoesNotTruncateExistingFiles() throws Exception {
        Path ttl = directory.resolve("output.ttl"), nq = directory.resolve("changes.nq");
        Files.write(ttl, new byte[]{1}); Files.write(nq, new byte[]{2});
        try {
            Thread.currentThread().interrupt();
            assertThrows(CancellationException.class, () -> ComparisonExportWriter.write(new SemanticDiff(), "", "", ttl, nq));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(ttl));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(nq));
    }

    @Test void propagatesFileFailure() throws Exception {
        assertThrows(IOException.class, () -> ComparisonExportWriter.write(new SemanticDiff(), "", "",
                directory.resolve("missing/output.ttl"), directory.resolve("changes.nq")));
        assertFalse(Files.exists(directory.resolve("changes.nq")));
    }

    @Test void writesOutputsLargerThanChildHeap() throws Exception {
        Path log = directory.resolve("probe.log");
        String cp = Paths.get("target/test-classes").toAbsolutePath() + File.pathSeparator
                + System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process child = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx32m", "-cp", cp, LargeExport.class.getName(), directory.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(child.waitFor(60, TimeUnit.SECONDS));
            assertEquals(0, child.exitValue(), new String(Files.readAllBytes(log), StandardCharsets.UTF_8));
            assertTrue(Files.size(directory.resolve("large.ttl")) >= 64L * 1024 * 1024);
            assertEquals(64L * 1024 * 1024, Files.size(directory.resolve("large.nq")));
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); }
        }
    }

    public static class LargeExport {
        public static void main(String[] args) throws Exception {
            char[] chars = new char[1024 * 1024]; Arrays.fill(chars, 'x');
            String fragment = new String(chars);
            SemanticDiff diff = new SemanticDiff();
            for (int i = 0; i < 64; i++) {
                diff.getActivities().put("a" + i, fragment);
                diff.getProvDMs().put("g" + i, fragment);
            }
            Path dir = Paths.get(args[0]);
            ComparisonExportWriter.write(diff, "", "", dir.resolve("large.ttl"), dir.resolve("large.nq"));
        }
    }
}
