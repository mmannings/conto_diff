package org;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gomma.diff.ContoDiffException;
import org.webdifftool.client.model.SemanticDiff;

/** Writes the existing serialized fragments without assembling whole-file copies. */
final class ComparisonExportWriter {
    static void write(SemanticDiff diff, String prefixes, String header,
                      Path metadata, Path changes) throws IOException {
        ContoDiffException.checkInterrupted();
        String newline = System.lineSeparator();
        try (Writer out = open(metadata)) {
            fragment(out, prefixes);
            fragment(out, newline);
            fragment(out, header);
            fragment(out, diff.getBaseEntity());
            fragment(out, newline);
            fragment(out, newline);
            for (String value : diff.getLocations().values()) fragment(out, value);
            for (String value : diff.getActivities().values()) fragment(out, value);
            for (String value : diff.getSourceEntities().values()) fragment(out, value);
            for (String value : diff.getSdiffEntities().values()) {
                fragment(out, value);
                fragment(out, newline);
            }
            fragment(out, diff.getSoftwareAgent());
        }
        ContoDiffException.checkInterrupted();
        try (Writer out = open(changes)) {
            for (String value : diff.getProvDMs().values()) fragment(out, value);
        }
        ContoDiffException.checkInterrupted();
    }

    private static Writer open(Path path) throws IOException {
        // Match String.getBytes(UTF_8) replacement behavior for malformed input.
        return new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8));
    }

    private static void fragment(Writer out, String value) throws IOException {
        ContoDiffException.checkInterrupted();
        String text = String.valueOf(value); // StringBuilder.append(null) wrote "null".
        for (int offset = 0; offset < text.length(); offset += 8192) {
            ContoDiffException.checkInterrupted();
            out.write(text, offset, Math.min(8192, text.length() - offset));
        }
    }
}
