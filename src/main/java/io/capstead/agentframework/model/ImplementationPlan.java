package io.capstead.agentframework.model;
import java.util.List;
import java.util.Map;
public record ImplementationPlan(WorkItem workItem,
 Map<AnalysisCategory,List<AnalysisEvidence>> observedEvidence,
 List<RepositoryChangeCandidate> proposedRepositoryChanges,
 List<String> proposedDependencyOrder,List<String> testRequirements,
 List<String> assumptionsToResolve,List<String> approvalGates,
 List<String> rollbackRequirements) {}
