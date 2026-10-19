package org.gomma.diff;

import java.util.concurrent.CancellationException;

/** A failed comparison must not be mistaken for an empty diff. */
public class ContoDiffException extends RuntimeException {
    public ContoDiffException(String message, Throwable cause) { super(message, cause); }

    public static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("COnto comparison interrupted");
        }
    }
}
