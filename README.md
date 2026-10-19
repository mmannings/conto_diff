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
