package io.capstead.agentframework.model;
import java.util.List;
import java.util.Map;
public record PullRequestReviewArtifact(WorkItem workItem,String decision,
 Map<ValidationStatus,Integer> statusCounts,List<RepositoryComparison> repositoryComparisons,List<GitChange> observedChanges,
 Map<ValidationStatus,List<RequirementCheck>> checks,List<String> limitations) {}
