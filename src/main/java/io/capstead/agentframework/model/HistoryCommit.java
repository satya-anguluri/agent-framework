package io.capstead.agentframework.model;

import java.util.*;

public record HistoryCommit(String sha,String committedAt,String subject,List<String> files,
                            Set<String> jiraKeys,Map<String,String> resolvedPaths){
 public HistoryCommit(String sha,String committedAt,String subject,List<String> files,Set<String> jiraKeys){
  this(sha,committedAt,subject,files,jiraKeys,identity(files));
 }
 public HistoryCommit{
  files=List.copyOf(files);jiraKeys=Set.copyOf(jiraKeys);resolvedPaths=Map.copyOf(resolvedPaths);
 }
 public String currentPath(String originalPath){return resolvedPaths.getOrDefault(originalPath,originalPath);}
 private static Map<String,String> identity(List<String> files){
  Map<String,String> result=new LinkedHashMap<>();files.forEach(path->result.put(path,path));return result;
 }
}
