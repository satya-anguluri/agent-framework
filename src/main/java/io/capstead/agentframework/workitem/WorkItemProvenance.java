package io.capstead.agentframework.workitem;

import io.capstead.agentframework.model.WorkItem;
import java.net.URI;
import java.net.URISyntaxException;

/** Removes credentials and non-essential URL components before provenance is persisted. */
public final class WorkItemProvenance {
    private WorkItemProvenance(){}

    public static URI sanitize(URI uri){
        if(uri==null)throw new IllegalArgumentException("source URI is required");
        if(!uri.isAbsolute())throw new IllegalArgumentException("source URI must be absolute");
        try{
            if(uri.isOpaque())return new URI(uri.getScheme(),uri.getSchemeSpecificPart(),null);
            return new URI(uri.getScheme(),null,uri.getHost(),uri.getPort(),uri.getPath(),null,null);
        }catch(URISyntaxException e){throw new IllegalArgumentException("Invalid source URI",e);}
    }

    public static WorkItem sanitize(WorkItem item){
        URI clean=sanitize(URI.create(item.sourceUrl()));
        return new WorkItem(item.key(),item.sourceSystem(),item.summary(),item.description(),
          item.acceptanceCriteria(),item.status(),clean.toString(),item.sourceUpdatedAt());
    }
}
