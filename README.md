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

The standalone artifact is `target/ContoDiff-1.1.3-shaded.jar`. Run from the
repository root, where the `rules/` directory is available:

```sh
java -jar target/ContoDiff-1.1.3-shaded.jar -oa old.owl -ob new.owl -o diff.txt
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

## Structured RDF export in 1.1.3

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

## Release 1.1.3 and registry publication

Version 1.1.3 includes the long-value and structured RDF export fixes. The shaded
build merges service-provider registrations, including RDF4J parser/writer
factories. `ShadedJarIT` runs after packaging with only the release JAR and the
probe's test classes on its child JVM classpath, then checks a computed move and
parses both N-Quads and Turtle. Always use `mvn clean verify`, not only `package`,
before publishing.

The build no longer generates `dependency-reduced-pom.xml`. An existing copy is
ignored and is not used for publication. `release/conto-pom.xml` is the explicit
consumer POM for the self-contained shaded JAR; publishing the source POM would
incorrectly require consumers to resolve the local GOMMA dependencies.

After merging the release branch, build from the updated main checkout with
Java 17 and record the source commit and artifact checksum:

```sh
mvn clean verify
git rev-parse HEAD
sha256sum target/ContoDiff-1.1.3-shaded.jar
```

Publish to the change-management project's Maven registry as
`org.dbs.leipzig:conto:1.1.3`. Do not overwrite an existing version. First check
**Deploy > Package registry** in GitLab project `86847758`; if `1.1.3` already
exists, verify its checksum instead of uploading another binary.

Authentication is required for publishing even though downloads are public.
For a local publish, use a GitLab deploy token with `write_package_registry`
permission for that project. In Bash, enter it without displaying or saving it
in shell history:

```bash
export GITLAB_MAVEN_TOKEN_HEADER=Deploy-Token
read -r -s -p 'GitLab deploy token: ' GITLAB_MAVEN_TOKEN
printf '\n'
export GITLAB_MAVEN_TOKEN
mvn --batch-mode --no-transfer-progress -s release/gitlab-settings.xml \
  org.apache.maven.plugins:maven-deploy-plugin:3.1.1:deploy-file \
  -Dfile=target/ContoDiff-1.1.3-shaded.jar \
  -DpomFile=release/conto-pom.xml \
  -DrepositoryId=gitlab-maven \
  -Durl=https://gitlab.com/api/v4/projects/86847758/packages/maven
unset GITLAB_MAVEN_TOKEN GITLAB_MAVEN_TOKEN_HEADER
```

Inside a GitLab CI job for project `86847758`, use `Job-Token` as the header and
set `GITLAB_MAVEN_TOKEN` from `CI_JOB_TOKEN` instead. Do not paste tokens into
source files, chat, command arguments, or debug logs. See
[GitLab Maven authentication](https://docs.gitlab.com/user/packages/maven_repository/#authenticate-to-the-package-registry).

Before updating the backend, download the published JAR and POM into a fresh
temporary directory, compare the JAR's SHA-256 with the built artifact, and
compare the POM with `release/conto-pom.xml`. Keep this package version for as
long as any backend release references it. JARs remain build artifacts, not Git
commits. Backend integration and historical-comparison retries are separate
steps; a successful release build does not establish that every historical
ontology can be processed.
