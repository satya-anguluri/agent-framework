package io.capstead.agentframework;

import io.capstead.agentframework.model.KnowledgeItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ContextQueryServiceTest {
 @TempDir Path root;

 @Test void returnsBoundedDeterministicEvidenceWithProvenance()throws Exception{
  initRepository();String sha=GitSupport.head(root);Path db=root.resolve("context.db");
  try(var store=new SqliteKnowledgeStore(db)){
   store.initialize();
   store.replaceRepository("marketplace",root,"main",sha,List.of(
    new KnowledgeItem("java-type","PreOrderController","preorder request entry point",
      "services/order/PreOrderController.java",12,30,sha),
    new KnowledgeItem("message-producer","orders.preordered","preorder event publisher",
      "services/order/PreOrderService.java",44,60,sha),
    new KnowledgeItem("message-consumer","orders.preordered","preorder inventory reservation",
      "services/inventory/PreOrderListener.java",18,28,sha)));
   var service=new ContextQueryService(store);
   var bundle=service.explain("How does the pre order work currently?",3);
   assertEquals(List.of("pre","order","preorder"),bundle.queryTerms());
   assertEquals(3,bundle.observedEvidence().size());
   assertTrue(bundle.observedEvidence().stream().allMatch(e->e.commitSha().equals(sha)));
   assertTrue(bundle.observedEvidence().stream().anyMatch(e->e.name().contains("PreOrder")));
   assertThrows(IllegalArgumentException.class,()->service.explain(" ",2));
   assertThrows(IllegalArgumentException.class,()->service.explain("preorder",101));
   assertTrue(service.explain("unfindableterm",10).observedEvidence().isEmpty());
  }
 }

 @Test void protocolReturnsHealthAndExplainAsJsonLines()throws Exception{
  initRepository();String sha=GitSupport.head(root);Path db=root.resolve("protocol.db");
  try(var store=new SqliteKnowledgeStore(db)){
   store.initialize();store.replaceRepository("marketplace",root,"main",sha,List.of(
    new KnowledgeItem("java-type","PreOrderService","preorder workflow",
      "PreOrderService.java",7,20,sha)));
  }
  String requests="""
    {"id":"1","method":"health"}
    {"id":"2","method":"explain","params":{"question":"How does preorder work?","limit":10}}
    """;
  var output=new ByteArrayOutputStream();
  new AgentProtocolServer(db).run(new ByteArrayInputStream(requests.getBytes(StandardCharsets.UTF_8)),output);
  String[] lines=output.toString(StandardCharsets.UTF_8).strip().split("\\R");
  assertEquals(2,lines.length);
  assertTrue(lines[0].contains("\"ready\":true"));
  assertTrue(lines[1].contains("\"PreOrderService\""));
  assertFalse(lines[1].contains(root.toAbsolutePath().toString()));
 }

 @Test void protocolRefusesStaleIndex()throws Exception{
  initRepository();String sha=GitSupport.head(root);Path db=root.resolve("stale.db");
  try(var store=new SqliteKnowledgeStore(db)){
   store.initialize();store.replaceRepository("marketplace",root,"main",sha,List.of(
    new KnowledgeItem("java-type","PreOrderService","preorder workflow","PreOrderService.java",1,2,sha)));
  }
  Files.writeString(root.resolve("change.txt"),"change");run("git","add","change.txt");run("git","commit","-m","advance");
  var output=new ByteArrayOutputStream();
  new AgentProtocolServer(db).run(new ByteArrayInputStream(
    "{\"id\":\"1\",\"method\":\"explain\",\"params\":{\"question\":\"preorder\"}}\n".getBytes(StandardCharsets.UTF_8)),output);
  assertTrue(output.toString(StandardCharsets.UTF_8).contains("STALE_INDEX"));
 }

 @Test void mcpListsAndCallsReadOnlyContextTools()throws Exception{
  initRepository();String sha=GitSupport.head(root);Path db=root.resolve("mcp.db");
  try(var store=new SqliteKnowledgeStore(db)){
   store.initialize();store.replaceRepository("marketplace",root,"main",sha,List.of(
    new KnowledgeItem("java-type","PreOrderService","preorder workflow","PreOrderService.java",7,20,sha)));
  }
  String requests="""
    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
    {"jsonrpc":"2.0","method":"notifications/initialized"}
    {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
    {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"explain_context","arguments":{"question":"How does preorder work?","limit":10}}}
    """;
  var output=new ByteArrayOutputStream();new McpServer(db).run(
    new ByteArrayInputStream(requests.getBytes(StandardCharsets.UTF_8)),output);
  String[] lines=output.toString(StandardCharsets.UTF_8).strip().split("\\R");
  assertEquals(3,lines.length);
  assertTrue(lines[0].contains("\"protocolVersion\":\"2025-06-18\""));
  assertTrue(lines[1].contains("\"explain_context\""));
  assertTrue(lines[2].contains("\"structuredContent\""));
  assertTrue(lines[2].contains("\"PreOrderService\""));
 }

 private void initRepository()throws Exception{
  run("git","init","--initial-branch=main");run("git","config","user.name","Test");
  run("git","config","user.email","test@example.invalid");
  Files.writeString(root.resolve("README.md"),"fixture");run("git","add","README.md");run("git","commit","-m","baseline");
 }
 private void run(String...command)throws Exception{
  var process=new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
  String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
  assertEquals(0,process.waitFor(),output);
 }
}
