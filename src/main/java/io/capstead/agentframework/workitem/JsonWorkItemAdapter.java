package io.capstead.agentframework.workitem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import io.capstead.agentframework.model.WorkItem;
import java.net.URI;

final class JsonWorkItemAdapter implements WorkItemAdapter {
    private static final ObjectMapper MAPPER=new ObjectMapper();
    public String id(){return "json";}
    public WorkItem read(JsonNode payload,URI sourceUri){
        try{
            WorkItem item=MAPPER.treeToValue(payload,WorkItem.class);
            if(item.sourceUrl()!=null&&!item.sourceUrl().isBlank())return item;
            return new WorkItem(item.key(),item.sourceSystem(),item.summary(),item.description(),
              item.acceptanceCriteria(),item.status(),sourceUri.toString(),item.sourceUpdatedAt());
        }
        catch(JsonProcessingException e){throw new IllegalArgumentException("Invalid canonical work-item JSON",e);}
    }
}
