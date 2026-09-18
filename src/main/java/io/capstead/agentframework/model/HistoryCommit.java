package io.capstead.agentframework.model;

import java.util.List;
import java.util.Set;

public record HistoryCommit(String sha,String committedAt,String subject,List<String> files,Set<String> jiraKeys){}
