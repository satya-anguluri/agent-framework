package io.capstead.agentframework.model;
public record GitChange(String repository,String status,String path,String previousPath,
 String baseCommit,String headCommit) {}
