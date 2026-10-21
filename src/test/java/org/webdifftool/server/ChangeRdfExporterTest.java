package org.webdifftool.server;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.gomma.diff.ContoDiffException;
import org.gomma.diff.DiffComputation;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.webdifftool.client.model.DiffEvolutionMapping;
import org.webdifftool.client.model.changes.Change;

import java.io.StringReader;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChangeRdfExporterTest {
    private static final String SUBCLASS = "http://www.w3.org/2000/01/rdf-schema#subClassOf";

    @Test void aggregateUsesUnderlyingChangesWithoutGuessingFragmentBoundaries() throws Exception {
        Change old = change("old", "delR", "https://example.org#A", "<" + SUBCLASS + ">", "https://www.metadata4ing.org#Old");
        Change added = change("new", "addR", "https://example.org#A", "<" + SUBCLASS + ">", "https://www.metadata4ing.org#New");
        Change move = change("move", "move");
        move.values = Arrays.asList(new String[]{"https://example.org#A"}, new String[]{SUBCLASS},
                new String[]{"https://www.metadata4ing.org", "Old"}, new String[]{"https://www.metadata4ing.org", "New"});
        move.mapsTo = Arrays.asList(old.id, added.id);
        Map<String, Change> all = new HashMap<>();
        for (Change c : Arrays.asList(old, added, move)) all.put(c.id, c);
        String before = move.getSimpleWordRepresentation();
        Model result = parse(ChangeRdfExporter.export(move.id, move, all));
        assertEquals(2, result.size());
        Set<String> objects = new HashSet<>();
        result.forEach(s -> {
            objects.add(s.getObject().stringValue());
            assertEquals("https://example.org/move_move", s.getContext().stringValue());
        });
        assertEquals(new HashSet<>(Arrays.asList("https://www.metadata4ing.org#Old", "https://www.metadata4ing.org#New")), objects);
        assertEquals(before, move.getSimpleWordRepresentation());
        assertEquals(Arrays.asList("old", "new"), move.mapsTo);
    }

    @Test void preservesLiteralPunctuationWhitespaceAndEscapes() throws Exception {
        String literal = "text, [brackets] #fragment \"quoted\" \\ newline\nnext";
        Change c = change("literal", "addA", "urn:test:A", "http://www.w3.org/2000/01/rdf-schema#label", literal);
        Model result = parse(ChangeRdfExporter.export(c.id, c, Collections.singletonMap(c.id,c)));
        assertEquals(1, result.size());
        assertEquals(SimpleValueFactory.getInstance().createLiteral(literal), result.iterator().next().getObject());
        Map<String,String> locations = new HashMap<>(), activities = new HashMap<>();
        OWLManagerCustom.generateLocationMap(Collections.singletonMap(c.id + "_" + c.name, ""), locations);
        OWLManagerCustom.generateActivityMap(locations, activities, "2222222222222222222222222222222222222222");
        String prefixes = "@prefix prov: <http://www.w3.org/ns/prov#> . "
                + "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> . "
                + "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> . "
                + "@prefix owl: <http://www.w3.org/2002/07/owl#> . ";
        Model metadata = Rio.parse(new StringReader(prefixes + String.join("\n", activities.values())), "", RDFFormat.TURTLE);
        assertTrue(metadata.contains(null, SimpleValueFactory.getInstance().createIRI("http://www.w3.org/ns/prov#addAttribute"), null));
    }

    @Test void preservesNonSubclassPredicatesAndCommasInsideValidIris() throws Exception {
        Change c = change("rel", "addR", "urn:test:A", "https://example.org/relatedTo", "https://example.org/a,b#c");
        Model result = parse(ChangeRdfExporter.export(c.id,c,Collections.singletonMap(c.id,c)));
        assertEquals("https://example.org/a,b#c", result.iterator().next().getObject().stringValue());
        assertEquals("https://example.org/relatedTo", result.iterator().next().getPredicate().stringValue());
    }

    @Test void refusesMalformedIrisInsteadOfRepairingOrSilentlyDroppingThem() {
        Change c = change("bad", "addR", "urn:test:A", SUBCLASS, "https://www.metadata4ing.org,");
        assertThrows(ContoDiffException.class, () -> ChangeRdfExporter.export(c.id,c,Collections.singletonMap(c.id,c)));
    }

    @Test void refusesMissingOrCyclicProvenance() {
        Change c = change("cycle", "move");
        assertThrows(ContoDiffException.class, () -> ChangeRdfExporter.export(c.id,c,Collections.singletonMap(c.id,c)));
        c.mapsTo = Collections.singletonList("missing");
        assertThrows(ContoDiffException.class, () -> ChangeRdfExporter.export(c.id,c,Collections.singletonMap(c.id,c)));
        c.mapsTo = Collections.singletonList(c.id);
        assertThrows(ContoDiffException.class, () -> ChangeRdfExporter.export(c.id,c,Collections.singletonMap(c.id,c)));
    }

    @Test void exportsActualComputedMoveWithoutMutatingDetectionResults() throws Exception {
        DiffEvolutionMapping mapping = new DiffComputation().computeDiff(ontology("Old"), ontology("New"));
        assertTrue(mapping.allChanges.values().stream().anyMatch(c -> c.name.equals("move")));
        Map<String,String> before = new HashMap<>();
        mapping.allChanges.forEach((id,c) -> before.put(id,c.getSimpleWordRepresentation()));
        for (Change c : mapping.allChanges.values()) {
            Model result = parse(ChangeRdfExporter.export(c.id,c,mapping.allChanges));
            assertFalse(result.isEmpty(), c.name);
            assertEquals(before.get(c.id), c.getSimpleWordRepresentation());
        }
    }

    @Test void publicApiWritesParseableMetadataAndFragmentIris(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        String prefix = "@prefix owl: <http://www.w3.org/2002/07/owl#> . "
                + "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> . "
                + "@prefix ex: <https://example.org#> . "
                + "ex:Ontology a owl:Ontology . ex:Old a owl:Class . ex:New a owl:Class . ";
        java.nio.file.Path left = directory.resolve("left.ttl"), right = directory.resolve("right.ttl");
        java.nio.file.Files.write(left, (prefix + "ex:A a owl:Class; rdfs:subClassOf ex:Old .").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.write(right, (prefix + "ex:A a owl:Class; rdfs:subClassOf ex:New; rdfs:label \"label, with spaces\" .").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Path metadata = directory.resolve("output.ttl"), quads = directory.resolve("changes.nq");
        org.webdifftool.client.model.DiffContext context = org.webdifftool.client.model.DiffContext.builder()
                .fileLeft(left.toString()).fileRight(right.toString())
                .rawUrlLeft("https://example.org/1111111111111111111111111111111111111111/ontology.ttl").rawUrlRight("https://example.org/2222222222222222222222222222222222222222/ontology.ttl")
                .leftCommitUri("https://example.org/commit/1111111111111111111111111111111111111111").rightCommitUri("https://example.org/commit/2222222222222222222222222222222222222222")
                .leftDatetime("2020-01-01T00:00:00Z").rightDatetime("2021-01-01T00:00:00Z")
                .leftMessage("before").rightMessage("after")
                .outputFile(metadata.toString()).allDiffsNQuadFile(quads.toString()).build();
        org.ContoDiffMain.makeContoDiff(context, "https://example.org/ontology.ttl");
        Model dataset;
        try (java.io.InputStream stream = java.nio.file.Files.newInputStream(quads)) {
            dataset = Rio.parse(stream, "", RDFFormat.NQUADS);
        }
        assertTrue(dataset.stream().anyMatch(st -> st.getObject().stringValue().equals("https://example.org#New")));
        Set<String> moved = new HashSet<>();
        dataset.stream().filter(st -> st.getContext().stringValue().endsWith("_move"))
                .forEach(st -> moved.add(st.getObject().stringValue()));
        assertEquals(new HashSet<>(Arrays.asList("https://example.org#Old", "https://example.org#New")), moved);
        try (java.io.InputStream stream = java.nio.file.Files.newInputStream(metadata)) {
            Model provenance = Rio.parse(stream, "", RDFFormat.TURTLE);
            for (org.eclipse.rdf4j.model.Resource graph : dataset.contexts()) {
                assertTrue(provenance.contains(null, SimpleValueFactory.getInstance().createIRI("http://www.w3.org/ns/prov#dm"), graph), graph.toString());
            }
        }
    }

    private OWLOntology ontology(String parent) throws Exception {
        org.semanticweb.owlapi.model.OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.createOntology();
        org.semanticweb.owlapi.model.OWLDataFactory f = manager.getOWLDataFactory();
        for (String name : Arrays.asList("A", "Old", "New"))
            manager.addAxiom(ontology,f.getOWLDeclarationAxiom(f.getOWLClass(IRI.create("https://example.org#"+name))));
        manager.addAxiom(ontology,f.getOWLSubClassOfAxiom(f.getOWLClass(IRI.create("https://example.org#A")),f.getOWLClass(IRI.create("https://example.org#"+parent))));
        return ontology;
    }

    private static Change change(String id, String name, String... values) {
        List<String[]> parameters = new ArrayList<>();
        for (String v : values) parameters.add(new String[]{v});
        return new Change(id,name,parameters);
    }
    private static Model parse(String nq) throws Exception {
        return Rio.parse(new StringReader(nq), "", RDFFormat.NQUADS);
    }
}
