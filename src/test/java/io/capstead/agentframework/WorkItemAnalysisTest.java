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
   store.upsertWorkItem(new WorkItem("PROJECT-9","jira","Different tracker item",null,null,"Open",
    "https://jira.example.com/browse/PROJECT-9","2026-09-19T01:00:00Z"));
   assertEquals("Change order events",store.workItem("linear:PROJECT-9").orElseThrow().summary());
   assertEquals("Different tracker item",store.workItem("jira:PROJECT-9").orElseThrow().summary());
   assertThrows(IllegalArgumentException.class,()->store.workItem("PROJECT-9"));
   store.replaceRepository("producer",root,"main",sha,List.of(
    new KnowledgeItem("message-producer","kafka:orders.created","order event delivery","Producer.java",1,1,sha),
    new KnowledgeItem("db-table","orders","order event delivery","db/migration/V2__orders.sql",1,1,sha),
    new KnowledgeItem("config-key","payment.image","order event delivery","charts/payment/values.yaml",1,1,sha),
    new KnowledgeItem("config-key","payment.timeout","order event delivery","src/main/resources/application.yml",1,1,sha),
    new KnowledgeItem("vault-path","secret/payment","order event delivery","vault/payment.hcl",1,1,sha),
    new KnowledgeItem("jenkins-stage","deploy","order event delivery","Jenkinsfile",1,1,sha)));
   store.replaceRepository("consumer",root,"main",sha,List.of(new KnowledgeItem(
    "message-consumer","kafka:orders.created","order event handler","Consumer.java",1,1,sha)));
   assertFalse(store.workItemEvidence("linear:PROJECT-9",20).isEmpty());
   assertFalse(store.workItemDependencies("linear:PROJECT-9",20).isEmpty());
   var report=new WorkItemAnalysisService(store).analyze("linear:PROJECT-9",30);
   assertTrue(report.observedEvidence().containsKey(AnalysisCategory.API_AND_MESSAGES));
   assertTrue(report.observedEvidence().containsKey(AnalysisCategory.DATA));
   assertTrue(report.observedEvidence().containsKey(AnalysisCategory.HELM_AND_KUBERNETES));
   assertTrue(report.observedEvidence().containsKey(AnalysisCategory.APPLICATION_CONFIGURATION));
   assertTrue(report.observedEvidence().containsKey(AnalysisCategory.VAULT));
   assertTrue(report.observedEvidence().containsKey(AnalysisCategory.CI_CD));
   assertTrue(report.requiredVerification().stream().anyMatch(v->v.contains("Vault")));
   assertTrue(report.rolloutAndRollbackChecks().stream().anyMatch(v->v.contains("rollback")));
   assertThrows(UnsupportedOperationException.class,()->report.resolvedDependencies().add("mutable"));
   assertNotNull(report.observedEvidence().values().iterator().next().getFirst().lineStart());
   var plan=new ImplementationPlanService().build(report);
   assertEquals(2,plan.proposedRepositoryChanges().size());
   assertTrue(plan.testRequirements().stream().anyMatch(v->v.contains("compatibility")));
   assertTrue(plan.approvalGates().stream().anyMatch(v->v.contains("Database owner")));
   assertTrue(plan.assumptionsToResolve().stream().allMatch(v->!v.isBlank()));
   assertThrows(UnsupportedOperationException.class,()->plan.proposedRepositoryChanges().add(null));
   String markdown=ImplementationPlanRenderer.markdown(plan);
   assertTrue(markdown.contains("## Observed evidence"));
   assertTrue(markdown.contains("## Approval gates"));
   assertTrue(markdown.contains("not a diagnosis"));
   assertTrue(markdown.contains("Evidence: order event delivery"));
   assertTrue(plan.proposedDependencyOrder().stream().anyMatch(v->v.contains("Source:")));
   Path exported=root.resolve("plans").resolve("PROJECT-9.md");
   AgentFramework.writeOutput(exported,markdown);
   assertEquals(markdown,java.nio.file.Files.readString(exported));
   var validation=new ImplementationValidationService().validate(plan,List.of(
    new GitChange("producer","M","Producer.java",sha,"b".repeat(40)),
    new GitChange("producer","M","src/test/java/ProducerTest.java",sha,"b".repeat(40)),
    new GitChange("producer","M","charts/payment/values.yaml",sha,"b".repeat(40))));
   assertEquals("HUMAN_REVIEW_REQUIRED",validation.decision());
   assertTrue(validation.requirementChecks().stream().anyMatch(c->c.status()==ValidationStatus.SATISFIED));
   assertTrue(validation.requirementChecks().stream().anyMatch(c->c.status()==ValidationStatus.UNVERIFIABLE));
   assertFalse(validation.requirementChecks().stream().anyMatch(c->c.status()==ValidationStatus.MISSING));
   assertThrows(UnsupportedOperationException.class,()->validation.observedChanges().add(null));
   var missingTests=new ImplementationValidationService().validate(plan,List.of(
    new GitChange("producer","M","Producer.java",sha,"b".repeat(40))));
   assertTrue(missingTests.requirementChecks().stream().anyMatch(c->c.status()==ValidationStatus.MISSING));
  }
 }
 @Test void acceptsTrackerNeutralKeys(){
  assertDoesNotThrow(()->new WorkItem("GH:42","github","Issue",null,null,"open",
   "https://github.com/example/repo/issues/42",null).validate());
 }
}
