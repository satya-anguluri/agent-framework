package io.capstead.agentframework.model;

public record RepositoryIndexStatus(
  String repository,
  String indexedCommit,
  String currentCommit,
  boolean current,
  String detail) {}
