package io.capstead.agentframework.model;

import java.net.URI;

public record JiraWorkItem(String key,String summary,String description,String acceptanceCriteria,
                           String sourceUrl,String sourceUpdatedAt){
 public void validate(){
  if(key==null||!key.matches("[A-Z][A-Z0-9]+-\\d+"))throw new IllegalArgumentException("Invalid Jira key");
  if(summary==null||summary.isBlank())throw new IllegalArgumentException("Jira summary is required");
  if(sourceUrl==null||sourceUrl.isBlank())throw new IllegalArgumentException("Jira sourceUrl is required");
  URI uri=URI.create(sourceUrl);
  if(!"https".equalsIgnoreCase(uri.getScheme()))throw new IllegalArgumentException("Jira sourceUrl must use HTTPS");
 }
}
