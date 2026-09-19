package io.capstead.agentframework;
import io.capstead.agentframework.model.*;
import java.util.*;

final class PullRequestReviewService {
 PullRequestReviewArtifact build(ImplementationValidationReport validation){
  EnumMap<ValidationStatus,List<RequirementCheck>> grouped=new EnumMap<>(ValidationStatus.class);
  for(ValidationStatus status:ValidationStatus.values())grouped.put(status,new ArrayList<>());
  for(RequirementCheck check:validation.requirementChecks())grouped.get(check.status()).add(check);
  EnumMap<ValidationStatus,Integer> counts=new EnumMap<>(ValidationStatus.class);
  Map<ValidationStatus,List<RequirementCheck>> immutable=new LinkedHashMap<>();
  for(ValidationStatus status:ValidationStatus.values()){
   List<RequirementCheck> rows=List.copyOf(grouped.get(status));
   counts.put(status,rows.size());immutable.put(status,rows);
  }
  return new PullRequestReviewArtifact(validation.workItem(),validation.decision(),
   Collections.unmodifiableMap(counts),List.copyOf(validation.repositoryComparisons()),List.copyOf(validation.observedChanges()),
   Collections.unmodifiableMap(immutable),List.copyOf(validation.limitations()));
 }
}
