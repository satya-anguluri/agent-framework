package io.capstead.agentframework.model;

import java.util.List;
import java.util.Map;

public record UnifiedAnalysisReport(
  WorkItem workItem,
  Map<AnalysisCategory,List<AnalysisEvidence>> observedEvidence,
  List<String> resolvedDependencies,
  List<String> relatedHistoricalWorkItems,
  List<String> requiredVerification,
  List<String> rolloutAndRollbackChecks) {}
