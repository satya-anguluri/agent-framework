package io.capstead.agentframework;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.capstead.agentframework.model.JiraWorkItem;
import io.capstead.agentframework.model.RepositoryConfig;
import picocli.CommandLine;
import picocli.CommandLine.*;
import java.nio.file.*;
import java.util.concurrent.Callable;

@Command(name="agent-framework",mixinStandardHelpOptions=true,
 subcommands={AgentFramework.Init.class,AgentFramework.Index.class,AgentFramework.Search.class,
 AgentFramework.BlastRadius.class,AgentFramework.JiraImport.class,AgentFramework.JiraShow.class,
 AgentFramework.JiraBlastRadius.class})
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
   if(!verifyCurrent(store))return 2;printBlastRadius(store.blastRadius(query,limit));
  }return 0;}}

 @Command(name="jira-import",description="Import a normalized Jira work item JSON document")
 static class JiraImport extends DbCommand implements Callable<Integer>{
  @Option(names="--file",required=true)Path file;
  public Integer call()throws Exception{
   JiraWorkItem item=new ObjectMapper().readValue(file.toFile(),JiraWorkItem.class);
   item.validate();
   try(var store=new SqliteKnowledgeStore(db)){store.initialize();store.upsertJiraWork(item);}
   System.out.printf("Imported %s from %s%n",item.key(),item.sourceUrl());return 0;}}

 @Command(name="jira-show",description="Show an imported Jira work item")
 static class JiraShow extends DbCommand implements Callable<Integer>{
  @Parameters(index="0")String key;
  public Integer call()throws Exception{try(var store=new SqliteKnowledgeStore(db)){
   JiraWorkItem item=store.jiraWork(key).orElseThrow(()->new ParameterException(spec.commandLine(),"Unknown Jira key: "+key));
   System.out.printf("%s — %s%nSource: %s%nSource updated: %s%n%n%s%n%nAcceptance criteria:%n%s%n",
    item.key(),item.summary(),item.sourceUrl(),item.sourceUpdatedAt(),item.description(),item.acceptanceCriteria());
  }return 0;}@Spec CommandSpec spec;}

 @Command(name="jira-blast-radius",description="Generate evidence matches from an imported Jira work item")
 static class JiraBlastRadius extends DbCommand implements Callable<Integer>{
  @Parameters(index="0")String key;@Option(names="--limit",defaultValue="50")int limit;
  public Integer call()throws Exception{try(var store=new SqliteKnowledgeStore(db)){
   if(!verifyCurrent(store))return 2;
   JiraWorkItem item=store.jiraWork(key).orElseThrow(()->new ParameterException(spec.commandLine(),"Unknown Jira key: "+key));
   System.out.printf("JIRA %s — %s%nSource: %s%n%n",item.key(),item.summary(),item.sourceUrl());
   printBlastRadius(store.jiraBlastRadius(key,limit));
  }return 0;}@Spec CommandSpec spec;}

 private static void printBlastRadius(java.util.List<String> rows){
  System.out.println("OBSERVED MATCHES AND DETERMINISTIC RELATIONSHIPS");
  if(rows.isEmpty())System.out.println("No indexed evidence matched this work item.");
  else rows.forEach(System.out::println);
  System.out.println("\nPOSSIBLE IMPACT (NOT DIAGNOSIS)");
  System.out.println("Verify current callers, APIs, messages, migrations, tests, compatibility, and rollout order.");
 }

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
