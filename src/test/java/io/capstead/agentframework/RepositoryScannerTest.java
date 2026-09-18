package io.capstead.agentframework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryScannerTest {
 @TempDir Path root;
 @Test void extractsTypesTablesRoutesAndSqlWithProvenance() throws Exception {
   Files.writeString(root.resolve("DeploymentController.java"), """
     @RestController class DeploymentController {
       @PostMapping("/deployments") void create() {}
     }
     @Entity @Table(name = "deployment") class DeploymentEntity {}
     """);
   Files.writeString(root.resolve("V1__history.sql"), "create table deployment_history(id bigint);");
   var items=new RepositoryScanner().scan(root,"0123456789012345678901234567890123456789");
   assertTrue(items.stream().anyMatch(i->i.kind().equals("java-type")&&i.name().equals("DeploymentController")));
   assertTrue(items.stream().anyMatch(i->i.kind().equals("db-table")&&i.name().equals("deployment")));
   assertTrue(items.stream().anyMatch(i->i.kind().equals("db-table")&&i.name().equals("deployment_history")));
   assertTrue(items.stream().allMatch(i->i.commitSha().length()==40));
 }
}
