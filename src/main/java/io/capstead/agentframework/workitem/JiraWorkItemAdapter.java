package io.capstead.agentframework.workitem;

import com.fasterxml.jackson.databind.JsonNode;
import io.capstead.agentframework.model.WorkItem;
import java.net.URI;

final class JiraWorkItemAdapter implements WorkItemAdapter {
    public String id(){return "jira";}
    public WorkItem read(JsonNode p,URI sourceUri){
        JsonNode fields=p.path("fields");
        WorkItem item=new WorkItem(required(p,"key"),"jira",required(fields,"summary"),
          content(fields.get("description")),content(fields.get("customfield_acceptance_criteria")),
          fields.path("status").path("name").asText(null),sourceUri.toString(),fields.path("updated").asText(null));
        item.validate();return item;
    }
    private static String required(JsonNode p,String field){String v=p.path(field).asText(null);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing Jira field: "+field);return v;}
    private static String content(JsonNode n){if(n==null||n.isNull()||n.isMissingNode())return null;return n.isTextual()?n.asText():n.toString();}
}
