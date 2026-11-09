package org.webdifftool.server;

import org.gomma.diff.utils.DataBaseHandler;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StringDocumentSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnnotationPropertyIriTest {
    private static final String LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String COMMENT = "http://www.w3.org/2000/01/rdf-schema#comment";
    private static final String SOURCE = "@prefix owl: <http://www.w3.org/2002/07/owl#> . "
            + "@prefix arbitrary: <http://www.w3.org/2000/01/rdf-schema#> . "
            + "<urn:test:ontology> a owl:Ontology . <urn:test:A> a owl:Class; "
            + "arbitrary:label \"Display name\"; arbitrary:comment \"Comment\"; "
            + "<urn:custom:annotation> \"Custom\"; <rdfs:label> \"Distinct IRI\" . "
            + "<urn:custom:annotation> a owl:AnnotationProperty . <rdfs:label> a owl:AnnotationProperty .";

    @Test void ontologyInputPreservesPropertyIdentityAndDisplayName() throws Exception {
        verify(false);
    }

    @Test void stringInputPreservesPropertyIdentityAndDisplayName() throws Exception {
        verify(true);
    }

    private void verify(boolean stringInput) throws Exception {
        try (DataBaseHandler database = new DataBaseHandler()) {
            OWLManagerCustom reader = new OWLManagerCustom(database);
            reader.conceptNames = new HashMap<>();
            if (stringInput) reader.readOWLString(SOURCE);
            else reader.readOWLOntology(OWLManager.createOWLOntologyManager()
                    .loadOntologyFromOntologyDocument(new StringDocumentSource(SOURCE)));
            Map<String,String> attributes = new HashMap<>();
            for (String[] row : reader.attributesImport) {
                assertEquals("urn:test:A", row[0]);
                assertNull(attributes.put(row[1], row[3]), "Properties must remain distinct");
            }
            assertEquals(4, attributes.size());
            assertEquals("Display name", attributes.get(LABEL));
            assertEquals("Comment", attributes.get(COMMENT));
            assertEquals("Custom", attributes.get("urn:custom:annotation"));
            assertEquals("Distinct IRI", attributes.get("rdfs:label"));
            assertEquals("Display name", reader.conceptNames.get("urn:test:A"));
        }
    }
}
