package io.capstead.agentframework;
import io.capstead.agentframework.extract.AnalysisCategoryRegistry;
import io.capstead.agentframework.model.*;
import java.util.*;

final class ImplementationValidationService {
 private final AnalysisCategoryRegistry categories=new AnalysisCategoryRegistry();

 ImplementationValidationReport validate(ImplementationPlan plan,List<GitChange> changes){
  List<GitChange> immutableChanges=List.copyOf(changes);
  List<RequirementCheck> checks=new ArrayList<>();
  Map<String,List<GitChange>> byRepo=new HashMap<>();
  for(GitChange change:changes)byRepo.computeIfAbsent(change.repository(),ignored->new ArrayList<>()).add(change);

  for(RepositoryChangeCandidate candidate:plan.proposedRepositoryChanges()){
   List<GitChange> repositoryChanges=byRepo.getOrDefault(candidate.repository(),List.of());
   List<String> matches=repositoryChanges.stream().filter(c->candidate.citedPaths().contains(c.path()))
    .map(ImplementationValidationService::provenance).toList();
   checks.add(new RequirementCheck("Review cited evidence in "+candidate.repository(),
    matches.isEmpty()?ValidationStatus.UNVERIFIABLE:ValidationStatus.SATISFIED,matches,
    matches.isEmpty()?"No cited candidate path changed; a different implementation may still be valid.":"Changed files intersect cited evidence."));
  }

  boolean productionChanged=changes.stream().anyMatch(c->!isTest(c.path()));
  List<String> testChanges=changes.stream().filter(c->isTest(c.path())).map(ImplementationValidationService::provenance).toList();
  checks.add(new RequirementCheck("Provide verification for confirmed behavior changes",
   !testChanges.isEmpty()?ValidationStatus.SATISFIED:productionChanged?ValidationStatus.MISSING:ValidationStatus.UNVERIFIABLE,
   testChanges,!testChanges.isEmpty()?"Test changes are present.":productionChanged?
    "Production files changed but no test file changed; document existing coverage or add tests.":"No production change was observed."));

  for(AnalysisCategory category:plan.observedEvidence().keySet()){
   List<String> categoryChanges=changes.stream().filter(c->categories.classify("",c.path())==category)
    .map(ImplementationValidationService::provenance).toList();
   checks.add(new RequirementCheck("Validate "+category+" impact",
    categoryChanges.isEmpty()?ValidationStatus.UNVERIFIABLE:ValidationStatus.SATISFIED,categoryChanges,
    categoryChanges.isEmpty()?"No changed path was deterministically classified in this category; inspect manually.":"Changed paths match this evidence category."));
  }

  checks.add(new RequirementCheck("Satisfy acceptance criteria",ValidationStatus.UNVERIFIABLE,List.of(),
   "Semantic acceptance criteria cannot be proven from changed filenames."));
  for(String gate:plan.approvalGates())checks.add(new RequirementCheck(gate,ValidationStatus.UNVERIFIABLE,List.of(),
   "Approval must be supplied by the responsible human or external system."));

  return new ImplementationValidationReport(plan.workItem(),immutableChanges,List.copyOf(checks),
   "HUMAN_REVIEW_REQUIRED",List.of(
    "SATISFIED means objective file evidence was found; it does not prove behavioral correctness.",
    "MISSING identifies absent expected evidence, not an automatic rejection.",
    "UNVERIFIABLE requires source inspection, execution evidence, or human approval.",
    "This report never approves, merges, deploys, or modifies the implementation."));
 }

 private static boolean isTest(String path){
  String p=path.toLowerCase(Locale.ROOT);
  return p.contains("/test/")||p.endsWith("test.java")||p.endsWith("tests.java")||p.contains("__tests__");
 }
 private static String provenance(GitChange c){return c.repository()+":"+c.path()+" ["+c.status()+"] "+c.baseCommit()+".."+c.headCommit();}
}
