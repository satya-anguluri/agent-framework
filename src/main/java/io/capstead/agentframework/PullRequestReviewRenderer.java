package io.capstead.agentframework;
import io.capstead.agentframework.model.*;
import java.util.*;

final class PullRequestReviewRenderer {
 private PullRequestReviewRenderer(){}
 static String markdown(PullRequestReviewArtifact artifact){
  StringBuilder out=new StringBuilder();
  out.append("# Pull-request review evidence: ").append(text(artifact.workItem().key())).append("\n\n");
  out.append("> Decision: **").append(text(artifact.decision())).append("**. This artifact does not approve the change.\n\n");
  out.append("## Work item\n\n");
  out.append("- Source: ").append(text(artifact.workItem().sourceSystem())).append("\n");
  out.append("- Summary: ").append(text(artifact.workItem().summary())).append("\n");
  out.append("- Provenance: ").append(text(artifact.workItem().sourceUrl())).append("\n\n");
  out.append("## Check summary\n\n| Status | Count |\n|---|---:|\n");
  for(ValidationStatus status:ValidationStatus.values())
   out.append("| ").append(status).append(" | ").append(artifact.statusCounts().get(status)).append(" |\n");
  out.append("\n## Repository comparisons\n\n");
  out.append("| Repository | Baseline | Head |\n|---|---|---|\n");
  for(RepositoryComparison comparison:artifact.repositoryComparisons())
   out.append("| ").append(cell(comparison.repository())).append(" | ").append(cell(comparison.baseCommit()))
    .append(" | ").append(cell(comparison.headCommit())).append(" |\n");
  out.append("\n## Observed committed changes\n\n");
  if(artifact.observedChanges().isEmpty())out.append("No committed changes were observed.\n");
  else{
   out.append("| Repository | Status | Path | Baseline | Head |\n|---|---|---|---|---|\n");
   for(GitChange change:artifact.observedChanges()){
    String path=change.previousPath()==null?change.path():change.previousPath()+" -> "+change.path();
    out.append("| ").append(cell(change.repository())).append(" | ").append(cell(change.status()))
     .append(" | ").append(cell(path)).append(" | ").append(cell(change.baseCommit()))
     .append(" | ").append(cell(change.headCommit())).append(" |\n");
   }
  }
  for(ValidationStatus status:ValidationStatus.values())checks(out,status,artifact.checks().get(status));
  out.append("\n## Limitations\n\n");
  artifact.limitations().forEach(item->out.append("- ").append(text(item)).append("\n"));
  out.append("\n---\nHuman review is required. This artifact does not post, approve, merge, deploy, or modify code.\n");
  return out.toString();
 }
 private static void checks(StringBuilder out,ValidationStatus status,List<RequirementCheck> checks){
  out.append("\n## ").append(status).append(" checks\n\n");
  if(checks.isEmpty()){out.append("None.\n");return;}
  for(RequirementCheck check:checks){
   out.append("- **").append(text(check.requirement())).append("** — ").append(text(check.reason())).append("\n");
   check.evidence().forEach(e->out.append("  - Evidence: ").append(text(e)).append("\n"));
  }
 }
 private static String cell(String value){return text(value).replace("\\","\\\\").replace("|","\\|");}
 private static String text(String value){
  return Objects.toString(value,"").replace("\r"," ").replace("\n"," ").replace("<","&lt;").replace(">","&gt;");
 }
}
