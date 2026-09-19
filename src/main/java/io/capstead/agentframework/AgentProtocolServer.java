package io.capstead.agentframework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class AgentProtocolServer {
  private final Path db;
  private final ObjectMapper mapper=new ObjectMapper();

  AgentProtocolServer(Path db){this.db=db;}

  void run(InputStream input,OutputStream output)throws IOException{
    try(var reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));
        var writer=new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8))){
      String line;
      while((line=reader.readLine())!=null){
        if(line.isBlank())continue;
        Map<String,Object> response;
        JsonNode request=null;
        try{
          request=mapper.readTree(line);
          response=handle(request);
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){
          response=error(null,"INVALID_JSON","Request must be one JSON object on a single line.");
        }catch(IllegalArgumentException e){
          response=error(request==null?null:text(request,"id"),"INVALID_REQUEST",e.getMessage());
        }catch(Exception e){
          response=error(request==null?null:text(request,"id"),"INTERNAL_ERROR","Unable to process the request.");
        }
        writer.write(mapper.writeValueAsString(response));writer.newLine();writer.flush();
      }
    }
  }

  private Map<String,Object> handle(JsonNode request)throws Exception{
    String id=text(request,"id"),method=text(request,"method");
    if(method==null||method.isBlank())return error(id,"INVALID_REQUEST","method is required");
    try(var store=new SqliteKnowledgeStore(db)){
      store.initialize();
      var status=new IndexStatusService().inspect(store);
      if(method.equals("health"))return success(id,status);
      if(method.equals("explain")){
        if(!status.ready())return error(id,"STALE_INDEX","Index is empty, stale, or cannot be verified.");
        JsonNode params=request.path("params");
        String question=text(params,"question");
        int limit=params.has("limit")?params.path("limit").asInt():25;
        return success(id,new ContextQueryService(store).explain(question,limit));
      }
      return error(id,"METHOD_NOT_FOUND","Unknown method: "+method);
    }
  }

  private Map<String,Object> success(String id,Object result){
    var response=new LinkedHashMap<String,Object>();response.put("id",id);response.put("ok",true);
    response.put("result",result);return response;
  }
  private Map<String,Object> error(String id,String code,String message){
    var detail=new LinkedHashMap<String,Object>();detail.put("code",code);detail.put("message",message);
    var response=new LinkedHashMap<String,Object>();response.put("id",id);response.put("ok",false);
    response.put("error",detail);return response;
  }
  private String text(JsonNode node,String field){
    if(node==null||!node.hasNonNull(field)||!node.path(field).isValueNode())return null;
    return node.path(field).asText();
  }
}
