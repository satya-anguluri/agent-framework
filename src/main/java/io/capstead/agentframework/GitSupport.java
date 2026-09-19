package io.capstead.agentframework;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import io.capstead.agentframework.model.GitChange;

final class GitSupport {
    private GitSupport() {}

    static String head(Path root) throws IOException, InterruptedException {
        String output = run(root, "rev-parse", "HEAD");
        if (!output.matches("[0-9a-fA-F]{40}")) throw new IOException("Invalid git HEAD for " + root);
        return output;
    }

    static String resolveCommit(Path root,String ref)throws IOException,InterruptedException{
        String output=run(root,"rev-parse","--verify",ref+"^{commit}");
        if(!output.matches("[0-9a-fA-F]{40}"))throw new IOException("Invalid git revision "+ref+" for "+root);
        return output;
    }

    static List<GitChange> changedFiles(String repository,Path root,String base,String head)throws IOException,InterruptedException{
        String baseCommit=resolveCommit(root,base),headCommit=resolveCommit(root,head);
        String output=run(root,"diff","--name-status",baseCommit+".."+headCommit,"--");
        if(output.isBlank())return List.of();
        List<GitChange> changes=new ArrayList<>();
        for(String line:output.split("\\R")){
            String[] fields=line.split("\\t");
            if(fields.length<2)throw new IOException("Unexpected git diff row for "+root+": "+line);
            String status=fields[0],path=fields[fields.length-1];
            String previousPath=(status.startsWith("R")||status.startsWith("C"))&&fields.length>=3?fields[1]:null;
            changes.add(new GitChange(repository,status,path,previousPath,baseCommit,headCommit));
        }
        return List.copyOf(changes);
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
