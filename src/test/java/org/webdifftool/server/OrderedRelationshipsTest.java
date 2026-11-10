package org.webdifftool.server;

import java.util.*;
import org.gomma.io.importer.models.ImportSourceStructure;
import org.gomma.io.importer.models.ImportSourceStructure.ImportObjRelationship;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderedRelationshipsTest {
    @Test void matchesLegacyEqualityAndFirstSeenOrder() {
        ImportSourceStructure legacy = new ImportSourceStructure();
        OrderedRelationships indexed = new OrderedRelationships();
        Random random = new Random(514);
        // Aa and BB have equal String hashes: collisions must not merge distinct triples.
        String[] values = {"Aa", "BB", "urn:a", "urn:b", "", "Unicode-ä"};
        for (int i = 0; i < 10000; i++) {
            String from = values[random.nextInt(values.length)];
            String to = values[random.nextInt(values.length)];
            String type = values[random.nextInt(values.length)];
            legacy.addRelationship(from, to, type);
            indexed.addRelationship(from, to, type);
        }
        assertEquals(rows(legacy.getRelationshipSet()), rows(indexed.getRelationshipSet()));
        assertEquals(216, rows(indexed.getRelationshipSet()).size());
    }

    @Test void keepsLargeDistinctInputAndDuplicateOrder() {
        OrderedRelationships indexed = new OrderedRelationships();
        for (int i = 0; i < 100000; i++) indexed.addRelationship("from" + i, "to", "type");
        for (int i = 99999; i >= 0; i--) indexed.addRelationship("from" + i, "to", "type");
        List<List<String>> result = rows(indexed.getRelationshipSet());
        assertEquals(100000, result.size());
        for (int i = 0; i < result.size(); i++) assertEquals(Arrays.asList("from" + i, "to", "type"), result.get(i));
    }

    @Test void interruptionStopsInsertionAndPreservesFlag() {
        OrderedRelationships indexed = new OrderedRelationships();
        indexed.addRelationship("before", "to", "type");
        try {
            Thread.currentThread().interrupt();
            assertThrows(java.util.concurrent.CancellationException.class, () -> indexed.addRelationship("after", "to", "type"));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, rows(indexed.getRelationshipSet()).size());
        } finally {
            Thread.interrupted();
        }
    }

    private static List<List<String>> rows(Iterable<ImportObjRelationship> relationships) {
        List<List<String>> result = new ArrayList<>();
        for (ImportObjRelationship r : relationships)
            result.add(Arrays.asList(r.getFromAccessionNumber(), r.getToAccessionNumber(), r.getType()));
        return result;
    }
}
