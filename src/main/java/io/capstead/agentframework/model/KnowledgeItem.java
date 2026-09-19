package io.capstead.agentframework.model;

public record KnowledgeItem(String kind, String name, String content, String sourcePath,
                            Integer lineStart, Integer lineEnd, String commitSha) {}
