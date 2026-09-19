package io.capstead.agentframework.workitem;

import com.fasterxml.jackson.databind.JsonNode;
import io.capstead.agentframework.model.WorkItem;
import java.net.URI;
import java.util.Map;

/** Converts a tracker-specific payload into the framework's canonical work-item model. */
public interface WorkItemAdapter {
    String id();
    WorkItem read(JsonNode payload, URI sourceUri);
    default WorkItem read(JsonNode payload,URI sourceUri,Map<String,String> options){
        return read(payload,sourceUri);
    }
}
