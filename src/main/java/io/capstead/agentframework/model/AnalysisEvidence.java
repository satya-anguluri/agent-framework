package io.capstead.agentframework.model;

public record AnalysisEvidence(
  AnalysisCategory category,String repository,String kind,String name,String sourcePath,
  Integer lineStart,String commitSha,String detail) {}
