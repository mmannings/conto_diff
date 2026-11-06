package org.gomma.diff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.webdifftool.client.model.DiffEvolutionMapping;
import org.webdifftool.client.model.changes.Change;

import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(60)
class ClassAnnotationMappingTest {
    private static final String CLASS = "urn:test:Class";
    private static final String PROPERTY = "urn:test:xref";

    private OWLOntology ontology(String value, boolean iriValue) throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.createOntology();
        OWLDataFactory factory = manager.getOWLDataFactory();
        manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(factory.getOWLClass(IRI.create(CLASS))));
        // Another class must not inherit the first class's annotations.
        manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(factory.getOWLClass(IRI.create("urn:test:Other"))));
        if (value != null) manager.addAxiom(ontology, factory.getOWLAnnotationAssertionAxiom(
                factory.getOWLAnnotationProperty(IRI.create(PROPERTY)), IRI.create(CLASS),
                iriValue ? IRI.create(value) : factory.getOWLLiteral(value)));
        return ontology;
    }

    private void assertAttribute(DiffEvolutionMapping mapping, String action, String value) {
        java.util.List<Change> changes = mapping.getAllChangesFromBasicMapping(action);
        assertEquals(1, changes.size(), action);
        java.util.List<String> values = new java.util.ArrayList<>();
        for (String[] group : changes.get(0).values) values.addAll(Arrays.asList(group));
        assertTrue(values.contains(CLASS), values.toString());
        assertTrue(values.contains(PROPERTY), values.toString());
        assertTrue(values.contains(value), values.toString());
        assertFalse(values.contains("urn:test:Other"));
    }

    @Test void changedLiteralProducesAdditionAndDeletion() throws Exception {
        DiffEvolutionMapping mapping = new DiffComputation().computeDiff(ontology("Before", false), ontology("After", false));
        assertAttribute(mapping, "delA", "Before");
        assertAttribute(mapping, "addA", "After");
    }

    @Test void iriAnnotationAdditionIsMapped() throws Exception {
        DiffEvolutionMapping mapping = new DiffComputation().computeDiff(ontology(null, true), ontology("urn:test:Reference", true));
        assertAttribute(mapping, "addA", "urn:test:Reference");
        assertTrue(mapping.getAllChangesFromBasicMapping("delA").isEmpty());
    }

    @Test void unchangedAnnotationProducesNoChanges() throws Exception {
        assertTrue(new DiffComputation().computeDiff(ontology("Same", false), ontology("Same", false)).allChanges.isEmpty());
    }
}
