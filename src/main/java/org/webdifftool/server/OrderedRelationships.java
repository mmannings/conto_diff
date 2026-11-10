package org.webdifftool.server;

import java.util.LinkedHashSet;
import java.util.Set;
import org.gomma.diff.ContoDiffException;
import org.gomma.io.importer.models.ImportSourceStructure;
import org.gomma.io.importer.models.ImportSourceStructure.ImportObjRelationship;

/** Import accumulator preserving Gomma relationship equality and first-seen order. */
final class OrderedRelationships {
    // Gomma's relationship is a non-static inner class; retain its original value type.
    private final ImportSourceStructure owner = new ImportSourceStructure();
    private final Set<ImportObjRelationship> relationships = new LinkedHashSet<>();

    void addRelationship(String from, String to, String type) {
        ContoDiffException.checkInterrupted();
        relationships.add(owner.new ImportObjRelationship(from, to, type));
    }

    Iterable<ImportObjRelationship> getRelationshipSet() {
        return relationships;
    }
}
