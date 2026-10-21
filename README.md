# ContoDiff
This project is the implementation of the ContoDiff approach enabling the diff computation for two ontology versions. The realization is based on a local h2 database for managing the two versions. The result is an evolution mapping consisting of additions, deletions, and changes of concepts, relationships, and attributes. Moreover, the method determines a complex diff regarding the basic diff. You can find more information in the following publication:

Michael Hartung, Anika Groß, Erhard Rahm:
<a href='https://dbs.uni-leipzig.de/file/contoDiff2013.pdf'>COnto-Diff: generation of complex evolution mappings for life science ontologies.</a> J. Biomed. Informatics 46(1): 15-32 (2013)


## Run
You can start the application by the following command line:

```
java org.ContoDiffMain -oa path_to_first_ontology -ob path_to_second_ontology -o outputfile
```

## Building and testing (Java 17)

Install the two bundled dependencies once, then build:

```sh
mvn install:install-file -Dfile=lib/enhanced_diff.jar -DgroupId=Gomma -DartifactId=gomma_enhanced_diff -Dversion=1.0 -Dpackaging=jar
mvn install:install-file -Dfile=lib/gomma-kernel.jar -DgroupId=Gomma -DartifactId=gomma_kernel -Dversion=1.0 -Dpackaging=jar
mvn clean verify
```

The standalone artifact is `target/ContoDiff-1.1.1-shaded.jar`. Run from the
repository root, where the `rules/` directory is available:

```sh
java -jar target/ContoDiff-1.1.1-shaded.jar -oa old.owl -ob new.owl -o diff.txt
```

## Comparison lifecycle in 1.1.0

Each `DiffComputation.computeDiff` call owns its executor and a uniquely named
in-memory H2 database. Overlapping calls cannot truncate or drop each other's
tables. The database is closed on success, failure, or cancellation, and ELK
reasoners are disposed after use. Database working data now consumes JVM heap;
size the application's heap for the ontologies being processed.

SQL and rule-loading failures throw `ContoDiffException` with the original cause,
instead of continuing with an incomplete result. Callers must record these as
failed comparisons and retry them separately. Interruption is checked at database
operations, ontology class processing, rule rounds, and before returning results.
This is cooperative cancellation; it cannot forcibly stop an external parser or
reasoner that ignores interruption, or isolate JVM out-of-memory failures.

The application entry points `ContoDiffMain.makeContoDiff` and
`DiffComputation.computeDiff` are unchanged. Internal singleton accessors were
removed: direct users of `DiffExecutor`, `RuleParser`, or the ontology managers
must pass their comparison's dependencies explicitly. Do not use the legacy
GOMMA entry points embedded in the dependency JAR to launch comparisons.

Regression tests cover overlapping calls on the same `DiffComputation`, SQL
failure followed by a successful comparison, missing rules, cancellation, and
database isolation and cleanup. Existing missing production diffs still need
recalculation after deploying the new library.

## JDBC driver loading in 1.1.1

H2 is loaded explicitly before opening the comparison database. This supports
hosts where JDBC discovery ran before the application's dependency class loader
was available, including executable Spring Boot deployments. Missing drivers
still fail with a `SQLException` retaining the original cause.

`JdbcDriverLoadingTest` launches a fresh JVM and initializes JDBC discovery with
a class loader that cannot see H2 before opening a comparison database. This
regression test fails with 1.1.0 and passes with explicit driver initialization.

## Structured RDF export

`ContoDiffMain.makeContoDiff` exports the existing computed `Change` objects via
`ChangeRdfExporter`. It no longer parses `getSimpleWordRepresentation()` or treats
list brackets, commas, or spaces as RDF syntax. RDF4J writes N-Quads and escapes
literal values. The operation graph identifiers and existing concept marker are
preserved. Basic relationship predicates are retained, including predicates
other than `rdfs:subClassOf`.

For complex changes, the exporter follows COnto's existing `mapsTo` provenance to
basic changes. Aggregated parameters use `#` as a delimiter, which can split
fragment IRIs; export does not guess how to reassemble those strings. Each
complex operation's graph contains the statements from its linked basic changes.
The rule engine, change names, IDs, parameters, and detection algorithms are
unchanged. Attribute values are exported as the strings supplied by COnto;
language tags or datatypes already discarded during computation are not invented.

Malformed IRIs, unsupported operations without underlying changes, and missing
or cyclic provenance raise `ContoDiffException` instead of silently generating
an incomplete graph. No historical ontology files or imports are repaired,
substituted, or synthesized. These failures remain the responsibility of their
ontology developers and maintainers. Empty results from unsupported edits in
the comparison model are not solved by this export change.

`ChangeRdfExporterTest` covers fragment IRIs in aggregated operations, arbitrary
relationship predicates, literal escaping, invalid IRIs, provenance failures,
and the public API's N-Quads and Turtle metadata. It also verifies that exporting
a computed mapping does not mutate its change values. The old display-string
export helpers in `OWLManagerCustom` were removed; the backend-facing
`makeContoDiff(DiffContext, String)` signature remains unchanged.
