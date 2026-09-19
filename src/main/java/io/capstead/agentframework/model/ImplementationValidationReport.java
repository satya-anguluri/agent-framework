package io.capstead.agentframework.model;
import java.util.List;
public record ImplementationValidationReport(WorkItem workItem,List<GitChange> observedChanges,
 List<RequirementCheck> requirementChecks,String decision,List<String> limitations) {}
