package io.capstead.agentframework;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.capstead.agentframework.model.JiraWorkItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class JiraWorkItemTest{
 @TempDir Path root;
 @Test void roundTripsNormalizedWorkItemWithSourceProvenance()throws Exception{
  JiraWorkItem item=new JiraWorkItem("FHB-1234","Store mapper status","Description","Given/when/then",
   "https://jira.example.com/browse/FHB-1234","2026-09-18T18:00:00Z");
  item.validate();
  try(var store=new SqliteKnowledgeStore(root.resolve("context.db"))){
   store.initialize();store.upsertJiraWork(item);
   assertEquals(item,store.jiraWork("FHB-1234").orElseThrow());
  }
 }
 @Test void rejectsUnprovenancedOrInvalidInput(){
  assertThrows(IllegalArgumentException.class,()->new JiraWorkItem("bad","x",null,null,"http://x",null).validate());
 }
}
