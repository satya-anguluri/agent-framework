package io.capstead.agentframework;

import io.capstead.agentframework.model.KnowledgeItem;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

final class RepositoryScanner {
    private static final Set<String> SKIP = Set.of(".git", "target", "build", ".idea", ".gradle", "node_modules");
    private static final Pattern JAVA_TYPE = Pattern.compile("\\b(class|interface|record|enum)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern TABLE = Pattern.compile("@Table\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern ROUTE = Pattern.compile("@(?:Get|Post|Put|Delete|Patch|Request)Mapping\\s*\\(([^)]*)\\)");
    private static final Pattern SQL_TABLE = Pattern.compile("(?i)\\b(?:create|alter)\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([\\w.\"]+)");

    List<KnowledgeItem> scan(Path root, String commit) throws IOException {
        List<KnowledgeItem> found = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).filter(p -> allowed(root.relativize(p))).forEach(path -> {
                try { extract(root, path, commit, found); } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) { throw e.getCause(); }
        return found;
    }

    private boolean allowed(Path relative) {
        for (Path part : relative) if (SKIP.contains(part.toString())) return false;
        String name = relative.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.startsWith(".env") || name.contains("secret") || name.contains("credential")) return false;
        return name.endsWith(".java") || name.endsWith(".sql") || name.endsWith(".md") ||
               name.equals("pom.xml") || name.endsWith(".yaml") || name.endsWith(".yml");
    }

    private void extract(Path root, Path path, String commit, List<KnowledgeItem> out) throws IOException {
        if (Files.size(path) > 1_000_000) return;
        String text = Files.readString(path);
        String rel = root.relativize(path).toString().replace('\\', '/');
        if (rel.endsWith(".java")) {
            matches(JAVA_TYPE, text, rel, commit, "java-type", 2, out);
            matches(TABLE, text, rel, commit, "db-table", 1, out);
            matches(ROUTE, text, rel, commit, "http-route", 1, out);
        } else if (rel.endsWith(".sql")) {
            matches(SQL_TABLE, text, rel, commit, "db-table", 1, out);
        } else {
            String title = text.lines().filter(s -> !s.isBlank()).findFirst().orElse(rel);
            out.add(new KnowledgeItem("document", title.substring(0, Math.min(title.length(), 160)),
                    text.substring(0, Math.min(text.length(), 20_000)), rel, 1, null, commit));
        }
    }

    private void matches(Pattern pattern, String text, String rel, String commit, String kind, int group,
                         List<KnowledgeItem> out) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            int line = 1 + (int) text.substring(0, m.start()).chars().filter(c -> c == '\n').count();
            int from = Math.max(0, m.start() - 180), to = Math.min(text.length(), m.end() + 300);
            out.add(new KnowledgeItem(kind, m.group(group), text.substring(from, to), rel, line, line, commit));
        }
    }
}
