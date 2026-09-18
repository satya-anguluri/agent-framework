package io.capstead.agentframework;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.capstead.agentframework.model.RepositoryConfig;
import picocli.CommandLine;
import picocli.CommandLine.*;
import java.nio.file.*;
import java.util.concurrent.Callable;

@Command(name="agent-framework",mixinStandardHelpOptions=true,
 subcommands={AgentFramework.Init.class,AgentFramework.Index.class,AgentFramework.Search.class,AgentFramework.BlastRadius.class})
public class AgentFramework implements Runnable{
 public static void main(String[]args){System.exit(new CommandLine(new AgentFramework()).execute(args));}
 public void run(){CommandLine.usage(this,System.out);}
 static abstract class DbCommand{@Option(names="--db",required=true)Path db;}

 @Command(name="init",description="Initialize the knowledge database")
 static class Init extends DbCommand implements Callable<Integer>{public Integer call()throws Exception{
  try(var s=new SqliteKnowledgeStore(db)){s.initialize();}return 0;}}

 @Command(name="index",description="Index configured clean local repositories")
 static class Index extends DbCommand implements Callable<Integer>{
  @Option(names="--config",required=true)Path config;
  public Integer call()throws Exception{
   RepositoryConfig cfg=new ObjectMapper().readValue(config.toFile(),RepositoryConfig.class);
   try(var store=new SqliteKnowledgeStore(db)){store.initialize();for(var repo:cfg.repositories()){
    Path root=Path.of(repo.localPath()).toAbsolutePath().normalize();
    GitSupport.requireClean(root);String sha=GitSupport.head(root);
    var items=new RepositoryScanner().scan(root,sha);
    store.replaceRepository(repo.name(),root,repo.defaultBranch(),sha,items);
    System.out.printf("Indexed %s: %d items at %s%n",repo.name(),items.size(),sha);
   }}return 0;}}

 @Command(name="search",description="Search current engineering context")
 static class Search extends DbCommand implements Callable<Integer>{
  @Parameters(index="0")String query;@Option(names="--limit",defaultValue="20")int limit;
  public Integer call()throws Exception{try(var store=new SqliteKnowledgeStore(db)){
   if(!verifyCurrent(store))return 2;
   for(String row:store.search(query,limit))System.out.println(row);}return 0;}}

 @Command(name="blast-radius",description="Expand matching objects across repository relationships")
 static class BlastRadius extends DbCommand implements Callable<Integer>{
  @Parameters(index="0")String query;@Option(names="--limit",defaultValue="50")int limit;
  public Integer call()throws Exception{try(var store=new SqliteKnowledgeStore(db)){
   if(!verifyCurrent(store))return 2;
   System.out.println("OBSERVED MATCHES AND DETERMINISTIC RELATIONSHIPS");
   for(String row:store.blastRadius(query,limit))System.out.println(row);
   System.out.println("\nPossible impact must be verified against current callers, tests, messages, and rollout order.");
  }return 0;}}

 private static boolean verifyCurrent(SqliteKnowledgeStore store)throws Exception{
  boolean current=true;
  for(var state:store.repositoryStates()){
   String actual=GitSupport.head(state.root());
   if(!actual.equals(state.indexedCommit())){current=false;System.err.printf(
    "STALE: %s indexed=%s current=%s; re-index before using this evidence%n",state.name(),state.indexedCommit(),actual);}
  }
  return current;
 }
}
