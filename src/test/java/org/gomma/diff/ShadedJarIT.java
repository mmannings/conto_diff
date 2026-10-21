package org.gomma.diff;

import java.nio.file.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import org.ContoDiffMain;
import org.webdifftool.client.model.DiffContext;
import org.eclipse.rdf4j.rio.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Verify service discovery using only the released JAR, not Maven dependencies. */
class ShadedJarIT {
    @TempDir Path directory;

    @Test void packagedJarExportsAndParsesBothRdfFormats() throws Exception {
        Path log = directory.resolve("smoke.log");
        String classpath = Paths.get("target/test-classes").toAbsolutePath()
                + File.pathSeparator + Paths.get(System.getProperty("conto.shadedJar")).toAbsolutePath();
        Process process = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, Probe.class.getName(), directory.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Packaged comparison timed out");
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
  Path dir=Paths.get(args[0]);
  String ttl="@prefix owl: <http://www.w3.org/2002/07/owl#> . @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> . @prefix ex: <https://example.org#> . ex:O a owl:Ontology . ex:Old a owl:Class . ex:New a owl:Class . ex:A a owl:Class; rdfs:subClassOf ex:";
  Files.write(dir.resolve("left.ttl"),(ttl+"Old .").getBytes(java.nio.charset.StandardCharsets.UTF_8));Files.write(dir.resolve("right.ttl"),(ttl+"New .").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  String old="1111111111111111111111111111111111111111", newer="2222222222222222222222222222222222222222";
  DiffContext context=DiffContext.builder().fileLeft(dir.resolve("left.ttl").toString()).fileRight(dir.resolve("right.ttl").toString())
   .rawUrlLeft("https://example.org/"+old+"/o.ttl").rawUrlRight("https://example.org/"+newer+"/o.ttl")
   .leftCommitUri("https://example.org/commit/"+old).rightCommitUri("https://example.org/commit/"+newer)
   .leftDatetime("2020-01-01T00:00:00Z").rightDatetime("2021-01-01T00:00:00Z").leftMessage("before").rightMessage("after")
   .outputFile(dir.resolve("output.ttl").toString()).allDiffsNQuadFile(dir.resolve("changes.nq").toString()).build();
  ContoDiffMain.makeContoDiff(context,"https://example.org/o.ttl");
  try(InputStream in=Files.newInputStream(dir.resolve("changes.nq"))) {
   org.eclipse.rdf4j.model.Model model=Rio.parse(in,"",RDFFormat.NQUADS);
   long count=model.stream().filter(s->s.getContext().stringValue().endsWith("_move")).count();
   if(count!=2) throw new AssertionError("Expected two move statements, got "+count);
  }
  try(InputStream in=Files.newInputStream(dir.resolve("output.ttl"))) { if(Rio.parse(in,"",RDFFormat.TURTLE).isEmpty())throw new AssertionError("Empty metadata"); }
  System.out.println("SHADED_JAR_SMOKE_PASSED");
 }
    }
}
