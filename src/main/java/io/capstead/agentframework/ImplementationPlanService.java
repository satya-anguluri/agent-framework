package io.capstead.agentframework;
import io.capstead.agentframework.model.*;
import java.util.*;
final class ImplementationPlanService {
 ImplementationPlan build(UnifiedAnalysisReport report){
  Map<String,List<AnalysisEvidence>> byRepository=new TreeMap<>();
  report.observedEvidence().values().stream().flatMap(Collection::stream)
   .forEach(row->byRepository.computeIfAbsent(row.repository(),ignored->new ArrayList<>()).add(row));
  List<RepositoryChangeCandidate> candidates=new ArrayList<>();
  byRepository.forEach((repository,rows)->{
   LinkedHashSet<String> paths=new LinkedHashSet<>();EnumSet<AnalysisCategory> categories=EnumSet.noneOf(AnalysisCategory.class);
   for(AnalysisEvidence row:rows){paths.add(row.sourcePath());categories.add(row.category());}
   candidates.add(new RepositoryChangeCandidate(repository,List.copyOf(paths),investigation(categories),testFocus(categories)));
  });
  List<String> order=new ArrayList<>();
  if(report.resolvedDependencies().isEmpty())order.add("No deterministic cross-repository edge was resolved; confirm whether repositories can deploy independently.");
  else for(String edge:report.resolvedDependencies())order.add("Confirm compatibility and rollout direction before ordering: "+edge);
  List<String> assumptions=new ArrayList<>();
  assumptions.add("Every cited artifact is a candidate for inspection, not a declaration that it must change.");
  assumptions.add("Acceptance criteria are complete and represent the current requested behavior.");
  if(report.observedEvidence().isEmpty())assumptions.add("No indexed evidence matched; refine the work item or inspect repositories manually.");
  if(!report.relatedHistoricalJiraContext().isEmpty())assumptions.add("Historical Jira context is informative only and may not describe current behavior.");
  return new ImplementationPlan(report.workItem(),report.observedEvidence(),List.copyOf(candidates),List.copyOf(order),
   tests(report.observedEvidence().keySet()),List.copyOf(assumptions),
   approvals(report.observedEvidence().keySet(),report.resolvedDependencies()),List.copyOf(report.rolloutAndRollbackChecks()));
 }
 private static List<String> investigation(Set<AnalysisCategory> c){
  List<String> x=new ArrayList<>();x.add("Inspect cited current source and confirm the smallest compatible change.");
  if(c.contains(AnalysisCategory.API_AND_MESSAGES))x.add("Trace contract producers, consumers, clients, retries, and idempotency.");
  if(c.contains(AnalysisCategory.DATA))x.add("Review schema ownership and design an expand/contract migration if data changes are required.");
  if(c.contains(AnalysisCategory.HELM_AND_KUBERNETES))x.add("Render and diff Helm/Kubernetes manifests for affected environments.");
  if(c.contains(AnalysisCategory.APPLICATION_CONFIGURATION))x.add("Trace configuration defaults and environment overrides.");
  if(c.contains(AnalysisCategory.VAULT))x.add("Verify Vault path and policy ownership without reading secret values.");
  if(c.contains(AnalysisCategory.CI_CD))x.add("Trace build, quality-gate, promotion, deployment, and rollback stages.");
  return List.copyOf(x);
 }
 private static List<String> testFocus(Set<AnalysisCategory> c){
  List<String> x=new ArrayList<>(List.of("Add or update focused unit tests for confirmed behavior changes.","Run existing repository verification before and after implementation."));
  if(c.contains(AnalysisCategory.API_AND_MESSAGES))x.add("Add compatibility tests for APIs/messages and affected clients or consumers.");
  if(c.contains(AnalysisCategory.DATA))x.add("Test migration forward compatibility, mixed-version operation, and supported rollback.");
  if(c.contains(AnalysisCategory.HELM_AND_KUBERNETES))x.add("Run Helm template/lint and Kubernetes schema validation.");
  if(c.contains(AnalysisCategory.APPLICATION_CONFIGURATION))x.add("Test missing, default, and environment-override configuration cases.");
  if(c.contains(AnalysisCategory.VAULT))x.add("Test authorization outcomes without storing or logging secrets.");
  if(c.contains(AnalysisCategory.CI_CD))x.add("Exercise pipeline gates and deployment/rollback paths in a non-production environment.");
  return List.copyOf(x);
 }
 private static List<String> tests(Set<AnalysisCategory> c){LinkedHashSet<String>x=new LinkedHashSet<>();x.add("Map each acceptance criterion to at least one automated or explicit manual verification.");x.addAll(testFocus(c));return List.copyOf(x);}
 private static List<String> approvals(Set<AnalysisCategory> c,List<String> d){
  List<String>x=new ArrayList<>(List.of("Requirement owner confirms acceptance criteria and resolved assumptions.","Repository owners approve the proposed scope after inspecting cited evidence.","CI verification passes on the exact commits intended for release."));
  if(c.contains(AnalysisCategory.API_AND_MESSAGES)||!d.isEmpty())x.add("API/message owners approve compatibility and cross-repository rollout order.");
  if(c.contains(AnalysisCategory.DATA))x.add("Database owner approves migration, repair, and rollback constraints.");
  if(c.contains(AnalysisCategory.VAULT))x.add("Security/platform owner approves Vault path and policy changes.");
  if(c.contains(AnalysisCategory.CI_CD)||c.contains(AnalysisCategory.HELM_AND_KUBERNETES))x.add("Release owner approves promotion, observability, abort thresholds, and rollback procedure.");
  return List.copyOf(x);
 }
}
