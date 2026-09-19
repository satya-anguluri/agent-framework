package io.capstead.agentframework;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GitHistoryScannerTest{
 @Test void extractsJiraKeysChangedFilesAndRenameLineage(){
  String log="\u001e"+"c".repeat(40)+"\u001f2025-06-01T03:04:05Z\u001fFHB-200 move mapper\n"+
   "R100\tsrc/old/MapperService.java\tsrc/main/MapperService.java\n"+
   "\u001e"+"a".repeat(40)+"\u001f2025-01-02T03:04:05Z\u001fFHB-123 add mapping for DEPLOY-9\n"+
   "M\tsrc/old/MapperService.java\nM\tdb/V10.sql\n"+
   "\u001e"+"b".repeat(40)+"\u001f2024-01-02T03:04:05Z\u001fmaintenance only\nM\tREADME.md\n";
  var commits=new GitHistoryScanner().parse(log);
  assertEquals(2,commits.size());
  var older=commits.stream().filter(c->c.jiraKeys().contains("FHB-123")).findFirst().orElseThrow();
  assertEquals("src/main/MapperService.java",older.currentPath("src/old/MapperService.java"));
  assertEquals("db/V10.sql",older.currentPath("db/V10.sql"));
  assertTrue(older.jiraKeys().contains("DEPLOY-9"));
 }
}
