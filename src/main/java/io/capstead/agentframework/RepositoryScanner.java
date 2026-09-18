package io.capstead.agentframework;

import io.capstead.agentframework.model.KnowledgeItem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

final class RepositoryScanner {
    private static final Set<String> SKIP = Set.of(".git", "target", "build", ".idea", ".gradle", "node_modules");
    private static final Pattern JAVA_TYPE = Pattern.compile("\\b(class|interface|record|enum)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern TABLE = Pattern.compile("@Table\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern ROUTE = Pattern.compile("@(Get|Post|Put|Delete|Patch|Request)Mapping(?:\\s*\\(([^)]*)\\))?");
    private static final Pattern SQL_TABLE = Pattern.compile("(?i)\\b(?:create|alter)\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([\\w.\"]+)");

    List<KnowledgeItem> scan(Path root, String commit) throws IOException {
        Map<String, KnowledgeItem> found = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).filter(p -> allowed(root.relativize(p))).forEach(path -> {
                try { extract(root, path, commit, found); } catch (IOException e) { throw new UncheckedIOException(e); }
            });
        } catch (UncheckedIOException e) { throw e.getCause(); }
        return List.copyOf(found.values());
    }

    private boolean allowed(Path relative) {
        for (Path part : relative) if (SKIP.contains(part.toString())) return false;
        String name = relative.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.startsWith(".") || name.contains("secret") || name.contains("credential") ||
                name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".properties")) return false;
        return name.endsWith(".java") || name.endsWith(".sql") || name.endsWith(".md") || name.equals("pom.xml");
    }

    private void extract(Path root, Path path, String commit, Map<String, KnowledgeItem> out) throws IOException {
        if (Files.size(path) > 1_000_000) return;
        String original = Files.readString(path);
        String rel = root.relativize(path).toString().replace('\\', '/');
        String scannable = rel.endsWith(".java") ? stripComments(original) :
                rel.endsWith(".sql") ? stripSqlComments(original) : original;
        if (rel.endsWith(".java")) {
            matches(JAVA_TYPE, scannable, original, rel, commit, "java-type", 2, out);
            matches(TABLE, scannable, original, rel, commit, "db-table", 1, out);
            Matcher routes = ROUTE.matcher(scannable);
            while (routes.find()) {
                String args = routes.group(2);
                add("http-route", routes.group(1).toUpperCase(Locale.ROOT) + ":" +
                        (args == null || args.isBlank() ? "<default>" : args.trim()),
                        original, routes.start(), routes.end(), rel, commit, out);
            }
        } else if (rel.endsWith(".sql")) {
            matches(SQL_TABLE, scannable, original, rel, commit, "db-table", 1, out);
        } else {
            String title = original.lines().filter(s -> !s.isBlank()).findFirst().orElse(rel);
            put(new KnowledgeItem("document", title.substring(0, Math.min(title.length(),160)),
                    original.substring(0,Math.min(original.length(),20_000)),rel,1,null,commit),out);
        }
    }

    private void matches(Pattern pattern, String scan, String original, String rel, String commit,
                         String kind, int group, Map<String, KnowledgeItem> out) {
        Matcher m=pattern.matcher(scan);
        while(m.find()) add(kind,m.group(group),original,m.start(),m.end(),rel,commit,out);
    }

    private void add(String kind,String name,String original,int start,int end,String rel,String commit,
                     Map<String,KnowledgeItem> out) {
        int line=1+(int)original.substring(0,start).chars().filter(c->c=='\n').count();
        int from=Math.max(0,start-180),to=Math.min(original.length(),end+300);
        put(new KnowledgeItem(kind,name,original.substring(from,to),rel,line,line,commit),out);
    }

    private void put(KnowledgeItem item,Map<String,KnowledgeItem> out) {
        out.putIfAbsent(item.kind()+"\u0000"+item.name()+"\u0000"+item.sourcePath(),item);
    }

    private String stripComments(String text) {
        StringBuilder out=new StringBuilder(text);
        boolean line=false,block=false,string=false,character=false,escape=false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i),n=i+1<text.length()?text.charAt(i+1):'\0';
            if(line){ if(c=='\n') line=false; else out.setCharAt(i,' '); continue; }
            if(block){ if(c=='*'&&n=='/'){out.setCharAt(i,' ');out.setCharAt(++i,' ');block=false;}
                else if(c!='\n') out.setCharAt(i,' '); continue; }
            if(string||character){ if(escape){escape=false;continue;} if(c=='\\'){escape=true;continue;}
                if(string&&c=='"')string=false; if(character&&c=='\'')character=false; continue; }
            if(c=='"'){string=true;continue;} if(c=='\''){character=true;continue;}
            if(c=='/'&&n=='/'){out.setCharAt(i,' ');out.setCharAt(++i,' ');line=true;}
            else if(c=='/'&&n=='*'){out.setCharAt(i,' ');out.setCharAt(++i,' ');block=true;}
        }
        return out.toString();
    }

    private String stripSqlComments(String text) {
        return text.replaceAll("(?m)--.*$"," ").replaceAll("(?s)/\\*.*?\\*/"," ");
    }
}
