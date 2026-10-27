package org.webdifftool.server;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.gomma.diff.ContoDiffException;
import org.webdifftool.client.model.changes.Change;

import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Serializes computed changes only; never parses their human-readable rendering. */
public final class ChangeRdfExporter {
    private static final SimpleValueFactory VALUES = SimpleValueFactory.getInstance();
    private static final IRI TYPE = VALUES.createIRI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
    // Preserve the integration's existing concept marker and graph identifiers.
    private static final IRI PROPERTY = VALUES.createIRI("http://www.w3.org/1999/02/22-rdf-syntax-ns#Property");

    private ChangeRdfExporter() {}

    public static String export(String hash, Change change, Map<String, Change> allChanges) {
        Model model = new LinkedHashModel();
        IRI graph = iri("https://example.org/" + hash + "_" + change.name);
        append(change, allChanges, graph, model, new HashSet<String>());
        StringWriter writer = new StringWriter();
        Rio.write(model, writer, RDFFormat.NQUADS);
        return writer.toString();
    }

    private static void append(Change change, Map<String, Change> allChanges, IRI graph,
                               Model model, Set<String> visiting) {
        ContoDiffException.checkInterrupted();
        if (!visiting.add(change.id)) throw failure("Cycle in change provenance: " + change.id);
        try {
            switch (change.name) {
                case "addC": case "delC":
                    concept(change, 0, graph, model);
                    break;
                case "addR": case "delR":
                    relationship(change, 0, graph, model);
                    break;
                case "mapR":
                    relationship(change, 0, graph, model);
                    relationship(change, 3, graph, model);
                    break;
                case "addA": case "delA":
                    attribute(change, 0, graph, model);
                    break;
                case "mapA":
                    attribute(change, 0, graph, model);
                    attribute(change, 3, graph, model);
                    break;
                case "mapC": case "idEvent":
                    concept(change, 0, graph, model);
                    concept(change, 1, graph, model);
                    break;
                default:
                    // Aggregated arrays use '#' as a delimiter even for fragment IRIs.
                    // Their original boundaries cannot be reconstructed safely. Use
                    // the basic changes already linked by COnto's rule engine instead.
                    if (change.mapsTo == null || change.mapsTo.isEmpty()) {
                        throw failure("Cannot export " + change.name + " (" + change.id
                                + "): no underlying changes available");
                    }
                    for (String id : change.mapsTo) {
                        Change underlying = allChanges.get(id);
                        if (underlying == null) throw failure("Missing underlying change: " + id);
                        append(underlying, allChanges, graph, model, visiting);
                    }
            }
        } finally {
            visiting.remove(change.id);
        }
    }

    private static void concept(Change c, int offset, IRI graph, Model model) {
        model.add(iri(single(c, offset)), TYPE, PROPERTY, graph);
    }

    private static void relationship(Change c, int offset, IRI graph, Model model) {
        model.add(iri(single(c, offset)), iri(single(c, offset + 1)),
                iri(single(c, offset + 2)), graph);
    }

    private static void attribute(Change c, int offset, IRI graph, Model model) {
        // COnto stores attribute values as strings; do not infer an IRI, datatype,
        // or language tag from their spelling or invent information it discarded.
        model.add(iri(single(c, offset)), iri(single(c, offset + 1)),
                VALUES.createLiteral(single(c, offset + 2)), graph);
    }

    private static String single(Change c, int index) {
        if (c.values == null || index >= c.values.size() || c.values.get(index) == null
                || c.values.get(index).length != 1 || c.values.get(index)[0] == null) {
            throw failure("Expected one value for " + c.name + " parameter " + index);
        }
        return c.values.get(index)[0];
    }

    private static IRI iri(String value) {
        // OWLAPI renders some predicates in angle brackets. Remove only that
        // explicit wrapper, never punctuation from the IRI itself.
        String raw = value.startsWith("<") && value.endsWith(">")
                ? value.substring(1, value.length() - 1) : value;
        try {
            URI uri = new URI(raw);
            // RDF identifiers need not be resolvable DNS hostnames. getHost()
            // rejects registered authorities such as SWO's urigen_local_uri.
            // Keep syntax validation and require an authority for HTTP(S).
            if (!uri.isAbsolute() || (("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme())) && uri.getRawAuthority() == null)) {
                throw failure("Cannot export invalid absolute IRI: " + value);
            }
            return VALUES.createIRI(raw);
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new ContoDiffException("Cannot export invalid absolute IRI: " + value, e);
        }
    }

    private static ContoDiffException failure(String message) {
        return new ContoDiffException(message, null);
    }
}
