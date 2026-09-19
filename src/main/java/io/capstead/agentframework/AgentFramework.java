package io.capstead.agentframework;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.capstead.agentframework.model.JiraWorkItem;
import io.capstead.agentframework.model.RepositoryConfig;
import io.capstead.agentframework.model.WorkItem;
import io.capstead.agentframework.workitem.WorkItemAdapters;
import io.capstead.agentframework.workitem.WorkItemProvenance;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;
import java.nio.file.*;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.*;

@Command(name="agent-framework",mixinStandardHelpOptions=true,
 subcommands={AgentFramework.Init.class,AgentFramework.Index.class,AgentFramework.Search.class,
 AgentFramework.BlastRadius.class,AgentFramework.JiraImport.class,AgentFramework.JiraShow.class,
 AgentFramework.JiraBlastRadius.class,AgentFramework.HistoryIndex.class,AgentFramework.JiraHistory.class,
 AgentFramework.RelatedJiras.class,AgentFramework.Dependencies.class,
 AgentFramework.WorkItemImport.class,AgentFramework.WorkItemShow.class,AgentFramework.AnalyzeWorkItem.class,
 AgentFramework.PlanWorkItem.class})
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


 @Command(name="history-index",description="Index Jira-linked commits and changed files for configured repositories")
 static class HistoryIndex extends DbCommand implements Callable<Integer>{
  @Option(names="--config",required=true)Path config;
  public Integer call()throws Exception{
   RepositoryConfig cfg=new ObjectMapper().readValue(config.toFile(),RepositoryConfig.class);
   try(var store=new SqliteKnowledgeStore(db)){store.initialize();for(var repo:cfg.repositories()){
    Path root=Path.of(repo.localPath()).toAbsolutePath().normalize();
    var commits=new GitHistoryScanner().scan(root);
    store.replaceHistory(repo.name(),commits);
    System.out.printf("Indexed %s history: %d Jira-linked commits%n",repo.name(),commits.size());
   }}return 0;}}

 @Command(name="jira-history",description="Show commits and changed files linked to a Jira key")
 static class JiraHistory extends DbCommand implements Callable<Integer>{
  @Parameters(index="0")String key;@Option(names="--limit",defaultValue="100")int limit;
  public Integer call()throws Exception{try(var store=new SqliteKnowledgeStore(db)){
   var rows=store.jiraHistory(key,limit);
   if(rows.isEmpty())System.out.println("No Jira-linked commits found for "+key.toUpperCase());
   else rows.forEach(System.out::println);
  }return 0;}}


 @Command(name="related-jiras",description="Find historical Jira work linked to current indexed code evidence")
 static class RelatedJiras extends DbCommand implements Callable<Integer>{
  @Parameters(index="0")String query;@Option(names="--limit",defaultValue="50")int limit;
  public Integer call()throws Exception{try(var store=new SqliteKnowledgeStore(db)){
   if(!verifyCurrent(store))return 2;
   var rows=store.relatedJiras(query,limit);
   System.out.println("HISTORICAL JIRAS LINKED THROUGH CURRENT CODE");
   if(rows.isEmpty())System.out.println("No historical Jira links matched the current evidence.");
   else rows.forEach(System.out::println);
   System.out.println("\nHistorical requirements are context only; verify them against current code and the new acceptance criteria.");
  }return 0;}}


 @Command(name="dependencies",description="Show deterministic cross-repository dependency edges")
 static class Dependencies extends DbCommand implements Callable<Integer>{
  public Integer call()throws Exception{try(var store=new SqliteKnowledgeStore(db)){
   if(!verifyCurrent(store))return 2;var rows=store.dependencyGraph();
   System.out.println("OBSERVED CROSS-REPOSITORY DEPENDENCIES");
   if(rows.isEmpty())System.out.println("No resolved dependency edges found.");else rows.forEach(System.out::println);
  }return 0;}}

 @Command(name="work-item-import",description="Import canonical or tracker-specific JSON")
 static class WorkItemImport extends DbCommand implements Callable<Integer>{
  @Option(names="--file",required=true)Path file;
  @Option(names="--adapter",defaultValue="json",description="Adapter: ${COMPLETION-CANDIDATES}")String adapter;
  @Option(names="--source-uri",description="Authoritative tracker URL for provenance")URI sourceUri;
  @Option(names="--adapter-option",description="Adapter mapping as key=value")Map<String,String> adapterOptions=new HashMap<>();
  public Integer call()throws Exception{
   URI origin=WorkItemProvenance.sanitize(sourceUri!=null?sourceUri:file.toAbsolutePath().toUri());
   var payload=new ObjectMapper().readTree(file.toFile());
   var item=WorkItemProvenance.sanitize(new WorkItemAdapters().require(adapter).read(payload,origin,adapterOptions));item.validate();
   try(var store=new SqliteKnowledgeStore(db)){store.initialize();store.upsertWorkItem(item);}
   System.out.println("Imported "+item.sourceSystem()+":"+item.key());return 0;
  }
 }
 @Command(name="work-item-show",description="Show a tracker-neutral work item")
 static class WorkItemShow extends DbCommand implements Callable<Integer>{
  @Parameters(index="0",description="Work-item key")String key;
  public Integer call()throws Exception{try(var store=new SqliteKnowledgeStore(db)){store.initialize();var item=store.workItem(key);if(item.isEmpty()){System.err.println("Work item not found: "+key);return 2;}System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(item.get()));}return 0;}
 }
 @Command(name="analyze-work-item",description="Generate a unified, provenance-first work-item analysis")
 static class AnalyzeWorkItem extends DbCommand implements Callable<Integer>{
  @Parameters(index="0",description="Work-item key")String key;
  @Option(names="--limit",defaultValue="25")int limit;
  @Option(names="--format",defaultValue="text",description="Output format: text or json")String format;
  public Integer call()throws Exception{
   if(limit<1){System.err.println("--limit must be positive");return 2;}
   if(!format.equals("text")&&!format.equals("json")){System.err.println("--format must be text or json");return 2;}
   try(var store=new SqliteKnowledgeStore(db)){
    store.initialize();if(!verifyCurrent(store))return 2;
    try{
     var report=new WorkItemAnalysisService(store).analyze(key,limit);
     if(format.equals("json"))System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report));
     else printUnifiedReport(report);
    }catch(IllegalArgumentException e){System.err.println(e.getMessage());return 2;}
   }return 0;
  }
 }
 @Command(name="plan-work-item",description="Export a reviewable implementation plan from current evidence")
 static class PlanWorkItem extends DbCommand implements Callable<Integer>{
  @Parameters(index="0",description="Work-item key")String key;
  @Option(names="--limit",defaultValue="50")int limit;
  @Option(names="--format",defaultValue="markdown",description="Output format: markdown or json")String format;
  @Option(names="--output",description="Optional output file; stdout when omitted")Path output;
  public Integer call()throws Exception{
   if(limit<1){System.err.println("--limit must be positive");return 2;}
   if(!format.equals("markdown")&&!format.equals("json")){System.err.println("--format must be markdown or json");return 2;}
   try(var store=new SqliteKnowledgeStore(db)){
    store.initialize();if(!verifyCurrent(store))return 2;
    try{
     var report=new WorkItemAnalysisService(store).analyze(key,limit);
     var plan=new ImplementationPlanService().build(report);
     String rendered=format.equals("json")
       ?new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(plan)
       :ImplementationPlanRenderer.markdown(plan);
     if(output==null)System.out.print(rendered);
     else{
      Path parent=output.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);
      Files.writeString(output,rendered,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
      System.out.println("Wrote implementation plan to "+output.toAbsolutePath());
     }
    }catch(IllegalArgumentException e){System.err.println(e.getMessage());return 2;}
   }return 0;
  }
 }

 private static void printUnifiedReport(io.capstead.agentframework.model.UnifiedAnalysisReport report)throws Exception{
  System.out.println("WORK ITEM");
  System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report.workItem()));
  System.out.println("\nOBSERVED CURRENT EVIDENCE");
  if(report.observedEvidence().isEmpty())System.out.println("No matching indexed evidence found.");
  else report.observedEvidence().forEach((category,rows)->{
   System.out.println("\n"+category);
   rows.forEach(row->System.out.printf("%s | %s | %s | %s:%s | %s%n  %s%n",
    row.repository(),row.kind(),row.name(),row.sourcePath(),
    row.lineStart()==null?"-":row.lineStart(),row.commitSha(),row.detail()));
  });
  printRows("RESOLVED DEPENDENCY EDGES",report.resolvedDependencies());
  printRows("RELATED HISTORICAL JIRA CONTEXT",report.relatedHistoricalJiraContext());
  printRows("REQUIRED VERIFICATION (NOT DIAGNOSIS)",report.requiredVerification());
  printRows("ROLLOUT AND ROLLBACK CHECKS",report.rolloutAndRollbackChecks());
 }
 private static void printRows(String title,java.util.List<String> rows){
  System.out.println("\n"+title);
  if(rows.isEmpty())System.out.println("No matching evidence found.");else rows.forEach(row->System.out.println("- "+row));
 }

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
