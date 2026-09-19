package io.capstead.agentframework.model;
import java.net.URI;
public record WorkItem(String key,String sourceSystem,String summary,String description,
 String acceptanceCriteria,String status,String sourceUrl,String sourceUpdatedAt){
 public void validate(){
  if(key==null||!key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{1,80}"))throw new IllegalArgumentException("Invalid work-item key");
  if(sourceSystem==null||sourceSystem.isBlank())throw new IllegalArgumentException("sourceSystem is required");
  if(summary==null||summary.isBlank())throw new IllegalArgumentException("summary is required");
  if(sourceUrl==null||sourceUrl.isBlank()||!"https".equalsIgnoreCase(URI.create(sourceUrl).getScheme()))
   throw new IllegalArgumentException("sourceUrl must use HTTPS");
 }
 public String searchableText(){return summary+" "+value(description)+" "+value(acceptanceCriteria);}
 private String value(String text){return text==null?"":text;}
}
