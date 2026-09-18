package io.capstead.agentframework;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GitHistoryScannerTest{
 @Test void extractsAllJiraKeysAndChangedFiles(){
  String log="\u001e"+"a".repeat(40)+"\u001f2025-01-02T03:04:05Z\u001fFHB-123 add mapping for DEPLOY-9\n"+
   "mapper/src/Mapper.java\ndb/V10.sql\n\n"+
   "\u001e"+"b".repeat(40)+"\u001f2024-01-02T03:04:05Z\u001fmaintenance only\nREADME.md\n";
  var commits=new GitHistoryScanner().parse(log);
  assertEquals(1,commits.size());
  assertEquals(2,commits.getFirst().jiraKeys().size());
  assertEquals(2,commits.getFirst().files().size());
  assertTrue(commits.getFirst().jiraKeys().contains("FHB-123"));
 }
}
