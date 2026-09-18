package io.capstead.agentframework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryScannerTest{
 @TempDir Path root;
 @Test void extractsSupportedFactsWithProvenanceAndDeduplicatesComments()throws Exception{
  Files.writeString(root.resolve("DeploymentController.java"),"""
    // class DeploymentController {}
    @RestController class DeploymentController {
      @GetMapping void health() {}
      @PostMapping("/deployments") void create() {}
    }
    @Entity @Table(name = "deployment") class DeploymentEntity {}
    """);
  Files.writeString(root.resolve("V1__history.sql"),"""
    -- create table deployment_history(id bigint);
    create table deployment_history(id bigint);
    """);
  var items=new RepositoryScanner().scan(root,"0123456789012345678901234567890123456789");
  assertEquals(1,items.stream().filter(i->i.kind().equals("java-type")&&i.name().equals("DeploymentController")).count());
  assertTrue(items.stream().anyMatch(i->i.kind().equals("http-route")&&i.name().equals("GET:<default>")));
  assertTrue(items.stream().anyMatch(i->i.kind().equals("db-table")&&i.name().equals("deployment")));
  assertEquals(1,items.stream().filter(i->i.kind().equals("db-table")&&i.name().equals("deployment_history")).count());
  assertTrue(items.stream().allMatch(i->i.commitSha().length()==40));
 }
 @Test void excludesConfigurationFilesThatMayContainSecrets()throws Exception{
  Files.writeString(root.resolve("application.yml"),"password: should-not-be-indexed");
  assertTrue(new RepositoryScanner().scan(root,"0123456789012345678901234567890123456789").isEmpty());
 }
}
