package io.capstead.agentframework;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.capstead.agentframework.model.RepositoryConfig;
import picocli.CommandLine;
import picocli.CommandLine.*;
import java.nio.file.*;
import java.util.concurrent.Callable;

@Command(name="agent-framework", mixinStandardHelpOptions=true,
 subcommands={AgentFramework.Init.class,AgentFramework.Index.class,AgentFramework.Search.class})
public class AgentFramework implements Runnable {
    public static void main(String[] args) { System.exit(new CommandLine(new AgentFramework()).execute(args)); }
    public void run(){ CommandLine.usage(this,System.out); }

    static abstract class DbCommand { @Option(names="--db",required=true) Path db; }

    @Command(name="init",description="Initialize the knowledge database")
    static class Init extends DbCommand implements Callable<Integer> {
        public Integer call() throws Exception { try(var s=new SqliteKnowledgeStore(db)){s.initialize();} return 0; }
    }

    @Command(name="index",description="Index configured local repositories")
    static class Index extends DbCommand implements Callable<Integer> {
        @Option(names="--config",required=true) Path config;
        public Integer call() throws Exception {
            RepositoryConfig cfg=new ObjectMapper().readValue(config.toFile(),RepositoryConfig.class);
            try(var store=new SqliteKnowledgeStore(db)) {
                store.initialize();
                for(var repo:cfg.repositories()) {
                    Path root=Path.of(repo.localPath()).toAbsolutePath().normalize();
                    String sha=GitSupport.head(root);
                    var items=new RepositoryScanner().scan(root,sha);
                    store.replaceRepository(repo.name(),root,repo.defaultBranch(),sha,items);
                    System.out.printf("Indexed %s: %d items at %s%n",repo.name(),items.size(),sha);
                }
            }
            return 0;
        }
    }

    @Command(name="search",description="Search indexed engineering context")
    static class Search extends DbCommand implements Callable<Integer> {
        @Parameters(index="0") String query;
        @Option(names="--limit",defaultValue="20") int limit;
        public Integer call() throws Exception {
            try(var store=new SqliteKnowledgeStore(db)) {
                for(String row:store.search(query,limit)) System.out.println(row);
            }
            return 0;
        }
    }
}
