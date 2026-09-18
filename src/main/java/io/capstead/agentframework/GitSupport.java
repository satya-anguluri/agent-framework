package io.capstead.agentframework;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

final class GitSupport {
    private GitSupport() {}

    static String head(Path root) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "-C", root.toString(), "rev-parse", "HEAD")
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0 || !output.matches("[0-9a-fA-F]{40}")) {
            throw new IOException("Cannot resolve git HEAD for " + root + ": " + output);
        }
        return output;
    }
}
