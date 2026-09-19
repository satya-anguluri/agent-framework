package io.capstead.agentframework.model;

public record ContextRelationship(
  String type,
  String evidence,
  String sourceRepository,
  String sourceKind,
  String sourceName,
  String sourcePath,
  Integer sourceLine,
  String sourceCommit,
  String targetRepository,
  String targetKind,
  String targetName,
  String targetPath,
  Integer targetLine,
  String targetCommit) {}
