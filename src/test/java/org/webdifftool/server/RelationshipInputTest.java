package org.webdifftool.server;

import java.util.*;
import org.gomma.diff.utils.DataBaseHandler;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StringDocumentSource;
import static org.junit.jupiter.api.Assertions.*;

class RelationshipInputTest {
    @Test void bothInputPathsPreserveSubclassAndDomainRelationships() throws Exception {
        String source = "@prefix owl: <http://www.w3.org/2002/07/owl#> . "
                + "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> . "
                + "<urn:test:ontology> a owl:Ontology . "
                + "<urn:test:A> a owl:Class . <urn:test:B> a owl:Class; rdfs:subClassOf <urn:test:A> . "
                + "<urn:test:p> a owl:ObjectProperty; rdfs:domain <urn:test:B> .";
        List<List<String>> previous = null;
        for (boolean stringInput : new boolean[]{false, true}) {
            try (DataBaseHandler db = new DataBaseHandler()) {
                OWLManagerCustom reader = new OWLManagerCustom(db);
                reader.conceptNames = new HashMap<>();
                if (stringInput) reader.readOWLString(source);
                else reader.readOWLOntology(OWLManager.createOWLOntologyManager()
                        .loadOntologyFromOntologyDocument(new StringDocumentSource(source)));
                List<List<String>> rows = new ArrayList<>();
                for (String[] row : reader.relationshipsImport) rows.add(Arrays.asList(row));
                assertTrue(rows.contains(Arrays.asList("urn:test:B", "urn:test:A", "<http://www.w3.org/2000/01/rdf-schema#subClassOf>")));
                assertTrue(rows.contains(Arrays.asList("urn:test:B", "urn:test:p", "<http://www.w3.org/2000/01/rdf-schema#subClassOf>")));
                assertEquals(rows.size(), new HashSet<>(rows).size());
                if (previous != null) assertEquals(new HashSet<>(previous), new HashSet<>(rows));
                previous = rows;
            }
        }
    }
}
