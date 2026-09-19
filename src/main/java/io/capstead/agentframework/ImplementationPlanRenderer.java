package io.capstead.agentframework;
import io.capstead.agentframework.model.*;
final class ImplementationPlanRenderer {
 private ImplementationPlanRenderer(){}
 static String markdown(ImplementationPlan plan){
  StringBuilder out=new StringBuilder();
  out.append("# Implementation Plan: ").append(plan.workItem().key()).append("\n\n");
  out.append("> Proposed plan derived from indexed evidence. It is not a diagnosis or authorization to change cited files.\n\n");
  out.append("## Work item\n\n- Source: ").append(plan.workItem().sourceSystem())
   .append("\n- Summary: ").append(plan.workItem().summary())
   .append("\n- Provenance: ").append(plan.workItem().sourceUrl()).append("\n\n");
  out.append("## Observed evidence\n");
  plan.observedEvidence().forEach((category,rows)->{
   out.append("\n### ").append(category).append("\n\n");
   for(AnalysisEvidence row:rows){
    out.append("- ").append(row.repository()).append("/").append(row.sourcePath());
    if(row.lineStart()!=null)out.append(":").append(row.lineStart());
    out.append(" @ ").append(row.commitSha()).append(" — ").append(row.kind()).append(" ").append(row.name()).append("\n");
    out.append("  - Evidence: ").append(java.util.Objects.toString(row.detail(),"").replace("\n"," ")).append("\n");
   }
  });
  out.append("\n## Proposed repository investigations\n");
  for(RepositoryChangeCandidate candidate:plan.proposedRepositoryChanges()){
   out.append("\n### ").append(candidate.repository()).append("\n\nCited paths:\n");
   candidate.citedPaths().forEach(path->out.append("- ").append(path).append("\n"));
   out.append("\nProposed investigation:\n");
   candidate.proposedInvestigation().forEach(item->out.append("- ").append(item).append("\n"));
   out.append("\nTest focus:\n");
   candidate.proposedTestFocus().forEach(item->out.append("- ").append(item).append("\n"));
  }
  section(out,"Proposed dependency order",plan.proposedDependencyOrder());
  section(out,"Test requirements",plan.testRequirements());
  section(out,"Assumptions to resolve",plan.assumptionsToResolve());
  section(out,"Approval gates",plan.approvalGates());
  section(out,"Rollback requirements",plan.rollbackRequirements());
  return out.toString();
 }
 private static void section(StringBuilder out,String title,java.util.List<String> rows){
  out.append("\n## ").append(title).append("\n\n");
  rows.forEach(row->out.append("- ").append(row.replace("\n"," ")).append("\n"));
 }
}
