package io.capstead.agentframework.workitem;

import com.fasterxml.jackson.databind.JsonNode;
import io.capstead.agentframework.model.WorkItem;
import java.net.URI;

final class LinearWorkItemAdapter implements WorkItemAdapter {
    public String id(){return "linear";}
    public WorkItem read(JsonNode p,URI sourceUri){
        JsonNode issue=p.has("data")&&p.path("data").has("issue")?p.path("data").path("issue"):p;
        WorkItem item=new WorkItem(required(issue,"identifier"),"linear",required(issue,"title"),
          issue.path("description").asText(null),null,issue.path("state").path("name").asText(null),
          issue.path("url").asText(sourceUri.toString()),issue.path("updatedAt").asText(null));
        item.validate();return item;
    }
    private static String required(JsonNode p,String field){String v=p.path(field).asText(null);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing Linear field: "+field);return v;}
}
