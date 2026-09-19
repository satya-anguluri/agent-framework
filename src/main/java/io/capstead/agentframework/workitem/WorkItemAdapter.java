package io.capstead.agentframework.workitem;

import com.fasterxml.jackson.databind.JsonNode;
import io.capstead.agentframework.model.WorkItem;
import java.net.URI;

/** Converts a tracker-specific payload into the framework's canonical work-item model. */
public interface WorkItemAdapter {
    String id();
    WorkItem read(JsonNode payload, URI sourceUri);
}
