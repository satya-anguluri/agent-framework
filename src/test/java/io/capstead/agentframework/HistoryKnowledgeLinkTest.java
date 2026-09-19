package io.capstead.agentframework;

import io.capstead.agentframework.model.HistoryCommit;
import io.capstead.agentframework.model.KnowledgeItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoryKnowledgeLinkTest{
 @TempDir Path root;
 @Test void findsHistoricalJiraThroughCurrentFileEvidence()throws Exception{
  String sha="a".repeat(40);
  try(var store=new SqliteKnowledgeStore(root.resolve("context.db"))){
   store.initialize();
   store.replaceRepository("mapper",root,"main",sha,List.of(
    new KnowledgeItem("java-type","MapperService","deployment mapping validation",
     "src/main/java/MapperService.java",1,1,sha)));
   store.replaceHistory("mapper",List.of(
    new HistoryCommit("b".repeat(40),"2025-01-02T03:04:05Z","FHB-742 add validation",
     List.of("src/main/java/MapperService.java"),Set.of("FHB-742"))));
   var results=store.relatedJiras("deployment",10);
   assertEquals(1,results.size());
   assertTrue(results.getFirst().contains("FHB-742"));
   assertTrue(results.getFirst().contains("MapperService"));
  }
 }
}
