package io.capstead.agentframework;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

final class GitSupport {
    private GitSupport() {}

    static String head(Path root) throws IOException, InterruptedException {
        String output = run(root, "rev-parse", "HEAD");
        if (!output.matches("[0-9a-fA-F]{40}")) throw new IOException("Invalid git HEAD for " + root);
        return output;
    }

    static void requireClean(Path root) throws IOException, InterruptedException {
        String status = run(root, "status", "--porcelain");
        if (!status.isBlank()) {
            throw new IOException("Refusing to index dirty worktree " + root +
                    "; commit, stash, or remove all tracked and untracked changes first");
        }
    }

    private static String run(Path root, String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 3];
        command[0] = "git"; command[1] = "-C"; command[2] = root.toString();
        System.arraycopy(args, 0, command, 3, args.length);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) throw new IOException("Git command failed for " + root + ": " + output);
        return output;
    }
}
