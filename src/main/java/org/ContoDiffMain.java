/*
 *
 *  * Copyright © 2014 - 2021 Leipzig University (Database Research Group)
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, version 3.
 *  *
 *  * This program is distributed in the hope that it will be useful, but
 *  * WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  * General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org;


import org.apache.commons.cli.*;
import org.gomma.diff.DiffComputation;
import org.gomma.diff.ContoDiffException;
import org.io.OntologyReader;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.webdifftool.client.model.DiffEvolutionMapping;
import org.webdifftool.client.model.DiffContext;
import org.webdifftool.client.model.SemanticDiff;
import org.webdifftool.client.model.changes.Change;
import org.webdifftool.server.OWLManagerCustom;
import org.webdifftool.server.ChangeRdfExporter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContoDiffMain {

    static Options options;

    static {
        options = new Options();
        Option inputFirstOnt = new Option(ConsoleConstants.INPUT_ONTOLOGY_A,
                "first ontology", true, "path of the first ontology");
        Option inputSecondOnt = new Option(ConsoleConstants.INPUT_ONTOLOGY_B, "path of the second ontology", true,
                "second ontology");
        Option outputFile =  new Option(ConsoleConstants.OUTPUT_FILE, "file with the compact diff representation", true,
                "output file");

        options.addOption(inputFirstOnt);
        options.addOption(inputSecondOnt);
        options.addOption(outputFile);
    }

    public static void main(String[] args) throws IOException {
        CommandLine cmd = parseCommand(args);
        OntologyReader reader = new OntologyReader();
        OWLOntology firstOnt = null;
        OWLOntology secontOnt = null;
        FileWriter output = null;
        try {
            System.out.println(cmd.getOptionValue(ConsoleConstants.INPUT_ONTOLOGY_A));
            System.out.println(cmd.getOptionValue(ConsoleConstants.INPUT_ONTOLOGY_B));
            File first = new File(cmd.getOptionValue(ConsoleConstants.INPUT_ONTOLOGY_A));
            File second = new File(cmd.getOptionValue(ConsoleConstants.INPUT_ONTOLOGY_B));
            output = new FileWriter(cmd.getOptionValue(ConsoleConstants.OUTPUT_FILE));
            firstOnt = reader.loadOntology(first);
            secontOnt = reader.loadOntology(second);
        } catch (NullPointerException e) {

            System.err.println("first or second ontology not found");
            e.printStackTrace();
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("load ontology "+ firstOnt.getClassesInSignature().size());
            DiffComputation computation = new DiffComputation();
            DiffEvolutionMapping mapping = computation.computeDiff(firstOnt, secontOnt);
            if (output != null){
                output.write(mapping.getFulltextOfCompactDiff());
            }
            output.close();

    }

    //used in external library
    public static void makeContoDiff(DiffContext diffContext, String ontologyIri) throws OWLOntologyCreationException, IOException {
        OntologyReader reader = new OntologyReader();
        OWLOntology left = setOntology(reader, diffContext.getFileLeft(), null);
        OWLOntology right = setOntology(reader, diffContext.getFileRight(), null);

        DiffComputation computation = new DiffComputation();
        DiffEvolutionMapping mapping = computation.computeDiff(left, right);
        Map<String, String> prefixes = OWLManagerCustom.getAllPrefixes(left, right);

        SemanticDiff sdiff = new SemanticDiff();

        for (Map.Entry<String, Change> change : mapping.allChanges.entrySet()) {
            sdiff.getProvDMs().put(change.getKey() + "_" + change.getValue().name,
                    ChangeRdfExporter.export(change.getKey(), change.getValue(), mapping.allChanges));
        }

        OWLManagerCustom.generateLocationMap(sdiff.getProvDMs(), sdiff.getLocations());


        sdiff.setBaseEntity(OWLManagerCustom.generateBaseEntity(ontologyIri));

        String secondSha1 = getSha1FromIri(diffContext.getRightCommitUri());

        OWLManagerCustom.generateActivityMap(sdiff.getLocations(), sdiff.getActivities(), secondSha1);

        String[] URIs = new String[]{diffContext.getRawUrlLeft(), diffContext.getRawUrlRight()};
        for (String rawUri : URIs) {
            OWLManagerCustom.generateSourceEntiry(rawUri, sdiff.getSourceEntities());
        }

        sdiff.setSoftwareAgent(OWLManagerCustom.generateSoftwareAgent());

        String firstSha1 = getSha1FromIri(diffContext.getLeftCommitUri());

        OWLManagerCustom.generateSDiffEntity(sdiff.getSourceEntities(), firstSha1, secondSha1, sdiff.getSdiffEntities(), ontologyIri, diffContext);

        StringBuilder sb = new StringBuilder();

        sb.append(OWLManagerCustom.prefixesMapToString(prefixes)).append(System.lineSeparator());
        sb.append(OWLManagerCustom.getAutoGeneratedHeader(left));
        sb.append(sdiff.getBaseEntity()).append(System.lineSeparator()).append(System.lineSeparator());

        for (Map.Entry<String, String> entry : sdiff.getLocations().entrySet()) {
            sb.append(entry.getValue());
        }

        for (Map.Entry<String, String> entry : sdiff.getActivities().entrySet()) {
            sb.append(entry.getValue());
        }

        for (Map.Entry<String, String> entry : sdiff.getSourceEntities().entrySet()) {
            sb.append(entry.getValue());
        }

        for (Map.Entry<String, String> entry : sdiff.getSdiffEntities().entrySet()) {
            sb.append(entry.getValue()).append(System.lineSeparator());
        }
        sb.append(sdiff.getSoftwareAgent());

        ContoDiffException.checkInterrupted();
        Files.write(Paths.get(diffContext.getOutputFile()), sb.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder diffs = new StringBuilder();
        for (String s : sdiff.getProvDMs().values()) {
            diffs.append(s);
        }
        Files.write(Paths.get(diffContext.getAllDiffsNQuadFile()), diffs.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static OWLOntology setOntology(OntologyReader reader, String file, String iri) throws IllegalArgumentException, OWLOntologyCreationException {
        if (file != null && iri != null) {
            throw new IllegalArgumentException("File and IRI are provided at the same time!");
        }
        else if (file != null) {
            return reader.loadOntology(new File(file));
        }
        else if (iri != null) {
            return reader.loadOntology(IRI.create(iri));
        }
        else return null;
    }

    private static CommandLine parseCommand(String[] args) {
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("utility-name", options);
            System.exit(1);
        }
        return cmd;
    }

    public static String getSha1FromIri(String iri) {
        String[] parts = iri.split("/");
        String firstSha1 = "";
        Pattern pattern = Pattern.compile("[0-9a-f]{40}");
        for (String part : parts) {
            Matcher matcher = pattern.matcher(part);
            if (matcher.matches()) {
                firstSha1 = part;
                break;
            }
        }
        return firstSha1;
    }
}
