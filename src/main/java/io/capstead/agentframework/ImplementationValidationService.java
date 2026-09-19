package io.capstead.agentframework;
import io.capstead.agentframework.extract.AnalysisCategoryRegistry;
import io.capstead.agentframework.model.*;
import java.util.*;

final class ImplementationValidationService {
 private final AnalysisCategoryRegistry categories=new AnalysisCategoryRegistry();

 ImplementationValidationReport validate(ImplementationPlan plan,List<GitChange> changes){
  List<GitChange> immutableChanges=List.copyOf(changes);List<RequirementCheck> checks=new ArrayList<>();
  Map<String,List<GitChange>> byRepo=new TreeMap<>();
  for(GitChange change:changes)byRepo.computeIfAbsent(change.repository(),ignored->new ArrayList<>()).add(change);

  for(RepositoryChangeCandidate candidate:plan.proposedRepositoryChanges()){
   List<GitChange> repositoryChanges=byRepo.getOrDefault(candidate.repository(),List.of());
   List<String> matches=repositoryChanges.stream().filter(c->candidate.citedPaths().contains(c.path())||
     c.previousPath()!=null&&candidate.citedPaths().contains(c.previousPath()))
    .map(ImplementationValidationService::provenance).toList();
   checks.add(new RequirementCheck("Review cited evidence in "+candidate.repository(),
    matches.isEmpty()?ValidationStatus.UNVERIFIABLE:ValidationStatus.SATISFIED,matches,
    matches.isEmpty()?"No cited candidate path changed; a different implementation may still be valid.":"Changed or renamed files intersect cited baseline evidence."));
  }

  byRepo.forEach((repository,repositoryChanges)->{
   boolean productionChanged=repositoryChanges.stream().anyMatch(c->!isTest(c.path()));
   List<String> tests=repositoryChanges.stream().filter(c->isTest(c.path())).map(ImplementationValidationService::provenance).toList();
   checks.add(new RequirementCheck("Provide verification for confirmed behavior changes in "+repository,
    !tests.isEmpty()?ValidationStatus.SATISFIED:productionChanged?ValidationStatus.MISSING:ValidationStatus.UNVERIFIABLE,
    tests,!tests.isEmpty()?"Test changes are present in the same repository; correspondence to behavior still requires review.":productionChanged?
     "Production files changed in this repository but no test file changed; document existing coverage or add tests.":"No production change was observed in this repository."));
  });

  for(AnalysisCategory category:plan.observedEvidence().keySet()){
   List<String> categoryChanges=changes.stream().filter(c->categories.classify("",c.path())==category||
     c.previousPath()!=null&&categories.classify("",c.previousPath())==category)
    .map(ImplementationValidationService::provenance).toList();
   checks.add(new RequirementCheck("Validate "+category+" impact",
    categoryChanges.isEmpty()?ValidationStatus.UNVERIFIABLE:ValidationStatus.SATISFIED,categoryChanges,
    categoryChanges.isEmpty()?"No changed path was deterministically classified in this category; inspect manually.":"Changed paths match this evidence category."));
  }

  checks.add(new RequirementCheck("Satisfy acceptance criteria",ValidationStatus.UNVERIFIABLE,List.of(),
   "Semantic acceptance criteria cannot be proven from changed filenames."));
  for(String gate:plan.approvalGates())checks.add(new RequirementCheck(gate,ValidationStatus.UNVERIFIABLE,List.of(),
   "Approval must be supplied by the responsible human or external system."));
  return new ImplementationValidationReport(plan.workItem(),immutableChanges,List.copyOf(checks),"HUMAN_REVIEW_REQUIRED",List.of(
   "SATISFIED means objective file evidence was found; it does not prove behavioral correctness.",
   "MISSING identifies absent expected evidence, not an automatic rejection.",
   "UNVERIFIABLE requires source inspection, execution evidence, or human approval.",
   "Test-file presence is correlated by repository only; behavioral correspondence still requires review.",
   "This report never approves, merges, deploys, or modifies the implementation."));
 }

 static boolean isTest(String path){
  String normalized=path.replace('\\','/');
  String p=normalized.toLowerCase(Locale.ROOT);
  String file=p.substring(p.lastIndexOf('/')+1);
  String originalFile=normalized.substring(normalized.lastIndexOf('/')+1);
  return p.startsWith("test/")||p.startsWith("tests/")||p.startsWith("spec/")||p.startsWith("specs/")||
   p.contains("/test/")||p.contains("/tests/")||p.contains("/spec/")||p.contains("/specs/")||
   p.contains("/__tests__/")||p.startsWith("__tests__/")||file.startsWith("test_")||
   file.contains("_test.")||file.contains(".test.")||file.contains(".spec.")||
   originalFile.endsWith("Test.java")||originalFile.endsWith("Tests.java");
 }
 private static String provenance(GitChange c){
  String paths=c.previousPath()==null?c.path():c.previousPath()+" -> "+c.path();
  return c.repository()+":"+paths+" ["+c.status()+"] "+c.baseCommit()+".."+c.headCommit();
 }
}
