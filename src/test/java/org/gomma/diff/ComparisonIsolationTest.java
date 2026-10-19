package org.gomma.diff;

import org.junit.jupiter.api.Test;
import org.gomma.diff.utils.DataBaseHandler;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Timeout(60)
class ComparisonIsolationTest {
    private OWLOntology ontology(boolean added) throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.createOntology();
        OWLDataFactory factory = manager.getOWLDataFactory();
        manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(factory.getOWLClass(IRI.create("urn:test:A"))));
        if (added) manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(factory.getOWLClass(IRI.create("urn:test:B"))));
        return ontology;
    }

    @Test void overlappingComparisonsDoNotDropEachOthersTables() throws Exception {
        DiffComputation computation = new DiffComputation();
        int expected = computation.computeDiff(ontology(false), ontology(true)).getAllChangesFromBasicMapping().size();
        assertTrue(expected > 0);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        OWLOntology original = ontology(false);
        OWLOntology blocked = (OWLOntology) Proxy.newProxyInstance(OWLOntology.class.getClassLoader(),
            new Class<?>[]{OWLOntology.class}, (proxy, method, args) -> {
                if (method.getName().equals("getClassesInSignature")) {
                    entered.countDown();
                    if (!release.await(30, TimeUnit.SECONDS)) throw new AssertionError("Comparison was not released");
                }
                try { return method.invoke(original, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> first = pool.submit(() -> computation.computeDiff(blocked, ontology(true)).getAllChangesFromBasicMapping().size());
            assertTrue(entered.await(30, TimeUnit.SECONDS));
            assertEquals(expected, computation.computeDiff(ontology(false), ontology(true)).getAllChangesFromBasicMapping().size());
            release.countDown();
            assertEquals(expected, first.get(30, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    @Test void failureDuringOntologyProcessingDoesNotPoisonNextComparison() throws Exception {
        OWLOntology original = ontology(false);
        OWLOntology failing = (OWLOntology) Proxy.newProxyInstance(OWLOntology.class.getClassLoader(),
            new Class<?>[]{OWLOntology.class}, (proxy, method, args) -> {
                if (method.getName().equals("getClassesInSignature")) throw new IllegalStateException("Injected ontology failure");
                try { return method.invoke(original, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            });
        DiffComputation computation = new DiffComputation();
        OWLOntology second = ontology(true);
        assertThrows(IllegalStateException.class, () -> computation.computeDiff(failing, second));
        assertFalse(computation.computeDiff(original, second).getAllChangesFromBasicMapping().isEmpty());
    }

    @Test void interruptionDuringComparisonAllowsRecovery() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Thread> worker = new java.util.concurrent.atomic.AtomicReference<>();
        OWLOntology original = ontology(false);
        OWLOntology blocked = (OWLOntology) Proxy.newProxyInstance(OWLOntology.class.getClassLoader(),
            new Class<?>[]{OWLOntology.class}, (proxy, method, args) -> {
                if (method.getName().equals("getClassesInSignature")) {
                    worker.set(Thread.currentThread());
                    entered.countDown();
                    try { new CountDownLatch(1).await(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                try { return method.invoke(original, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> result = pool.submit(() -> new DiffComputation().computeDiff(blocked, original));
            assertTrue(entered.await(30, TimeUnit.SECONDS));
            worker.get().interrupt();
            ExecutionException error = assertThrows(ExecutionException.class, () -> result.get(30, TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class, error.getCause());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
        assertFalse(new DiffComputation().computeDiff(original, ontology(true)).getAllChangesFromBasicMapping().isEmpty());
    }

    @Test void databaseIsPrivateAndClosedAfterUse() throws Exception {
        Connection connection;
        try (DataBaseHandler first = new DataBaseHandler(); DataBaseHandler second = new DataBaseHandler()) {
            connection = first.getDatabaseConnection();
            first.executeDml("CREATE TABLE private_table (id INT)");
            assertThrows(SQLException.class, () -> second.executeSelect("SELECT * FROM private_table"));
            ResultSet results = first.executeSelect("SELECT * FROM private_table");
            Statement statement = results.getStatement();
            first.closeStatement(results);
            assertTrue(statement.isClosed());
        }
        assertTrue(connection.isClosed());
    }

    @Test void sqlFailureIsReportedAndNextComparisonWorks() throws Exception {
        try (DataBaseHandler database = new DataBaseHandler()) {
            DiffExecutor executor = new DiffExecutor(database);
            executor.setupRepository();
            database.executeDml("DROP TABLE evol_change");
            ContoDiffException error = assertThrows(ContoDiffException.class, executor::computeBasicChanges);
            assertInstanceOf(SQLException.class, error.getCause());
        }
        assertFalse(new DiffComputation().computeDiff(ontology(false), ontology(true)).getAllChangesFromBasicMapping().isEmpty());
    }

    @Test void missingRulesAreReported() {
        try (DataBaseHandler database = new DataBaseHandler()) {
            assertThrows(ContoDiffException.class, () -> new DiffExecutor(database).loadRules("missing-rules.xml"));
            assertThrows(ContoDiffException.class, () -> new DiffExecutor(database).loadChangeActionDesc("missing-actions.xml"));
        }
    }

    @Test void interruptionDoesNotBecomeAnEmptyDiff() throws Exception {
        OWLOntology first = ontology(false), second = ontology(true);
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> new DiffComputation().computeDiff(first, second));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertFalse(new DiffComputation().computeDiff(first, second).getAllChangesFromBasicMapping().isEmpty());
    }
}
