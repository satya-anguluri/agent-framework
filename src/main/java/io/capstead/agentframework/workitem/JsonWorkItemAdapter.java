package io.capstead.agentframework.workitem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import io.capstead.agentframework.model.WorkItem;
import java.net.URI;

final class JsonWorkItemAdapter implements WorkItemAdapter {
    private static final ObjectMapper MAPPER=new ObjectMapper();
    public String id(){return "json";}
    public WorkItem read(JsonNode payload,URI sourceUri){
        try{return MAPPER.treeToValue(payload,WorkItem.class);}
        catch(JsonProcessingException e){throw new IllegalArgumentException("Invalid canonical work-item JSON",e);}
    }
}
