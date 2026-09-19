package io.capstead.agentframework;
import io.capstead.agentframework.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WorkItemAnalysisTest{
 @TempDir Path root;
 @Test void assemblesCurrentAndDependencyEvidence()throws Exception{
  String sha="a".repeat(40);
  WorkItem item=new WorkItem("PROJECT-9","linear","Change order events","Update order event delivery",
   "Producer and consumer remain compatible","Open","https://tracker.example.com/PROJECT-9","2026-09-19T01:00:00Z");
  try(var store=new SqliteKnowledgeStore(root.resolve("context.db"))){
   store.initialize();store.upsertWorkItem(item);
   store.replaceRepository("producer",root,"main",sha,List.of(new KnowledgeItem(
    "message-producer","kafka:orders.created","order event delivery","Producer.java",1,1,sha)));
   store.replaceRepository("consumer",root,"main",sha,List.of(new KnowledgeItem(
    "message-consumer","kafka:orders.created","order event handler","Consumer.java",1,1,sha)));
   assertFalse(store.workItemEvidence("PROJECT-9",20).isEmpty());
   assertFalse(store.workItemDependencies("PROJECT-9",20).isEmpty());
  }
 }
 @Test void acceptsTrackerNeutralKeys(){
  assertDoesNotThrow(()->new WorkItem("GH:42","github","Issue",null,null,"open",
   "https://github.com/example/repo/issues/42",null).validate());
 }
}
