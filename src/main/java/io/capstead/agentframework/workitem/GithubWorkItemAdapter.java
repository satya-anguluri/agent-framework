package io.capstead.agentframework.workitem;

import com.fasterxml.jackson.databind.JsonNode;
import io.capstead.agentframework.model.WorkItem;
import java.net.URI;

final class GithubWorkItemAdapter implements WorkItemAdapter {
    public String id(){return "github";}
    public WorkItem read(JsonNode p,URI sourceUri){
        String number=required(p,"number");
        String repository=repository(sourceUri);
        return validated(new WorkItem(repository+":"+number,"github",required(p,"title"),
          text(p,"body"),null,text(p,"state"),sourceUri.toString(),text(p,"updated_at")));
    }
    private static String repository(URI uri){
        String[] parts=uri.getPath().split("/");
        for(int i=0;i+2<parts.length;i++)if(parts[i].equals("repos"))return clean(parts[i+1])+"."+clean(parts[i+2]);
        if(parts.length>=3)return clean(parts[parts.length-2])+"."+clean(parts[parts.length-1]);
        throw new IllegalArgumentException("GitHub source URI must identify an owner and repository");
    }
    private static String clean(String value){return value.replaceAll("[^A-Za-z0-9._:-]","-");}
    private static String required(JsonNode p,String field){String v=text(p,field);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing GitHub field: "+field);return v;}
    private static String text(JsonNode p,String field){JsonNode n=p.get(field);return n==null||n.isNull()?null:n.asText();}
    private static WorkItem validated(WorkItem item){item.validate();return item;}
}
