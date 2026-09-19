package io.capstead.agentframework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

final class McpServer {
  static final String PROTOCOL_VERSION="2025-06-18";
  private final Path db;
  private final ObjectMapper mapper=new ObjectMapper();
  private boolean initialized;

  McpServer(Path db){this.db=db;}

  void run(InputStream input,OutputStream output)throws IOException{
    try(var reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));
        var writer=new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8))){
      String line;
      while((line=reader.readLine())!=null){
        if(line.isBlank())continue;
        Map<String,Object> response;
        try{
          JsonNode request=mapper.readTree(line);
          response=handle(request);
        }catch(JsonProcessingException e){response=error(null,-32700,"Parse error");}
        catch(IllegalArgumentException e){response=error(null,-32602,e.getMessage());}
        catch(Exception e){response=error(null,-32603,"Internal error");}
        if(response!=null){writer.write(mapper.writeValueAsString(response));writer.newLine();writer.flush();}
      }
    }
  }

  private Map<String,Object> handle(JsonNode request)throws Exception{
    JsonNode id=request.get("id");String method=text(request,"method");
    if(!"2.0".equals(text(request,"jsonrpc")))return error(id,-32600,"Invalid JSON-RPC request");
    if(method==null)return error(id,-32600,"Method is required");
    if(method.equals("initialize")){
      String requested=text(request.path("params"),"protocolVersion");
      String version=PROTOCOL_VERSION.equals(requested)?requested:PROTOCOL_VERSION;
      return result(id,Map.of(
        "protocolVersion",version,
        "capabilities",Map.of("tools",Map.of("listChanged",false)),
        "serverInfo",Map.of("name","agent-framework","version","0.1.0"),
        "instructions","Use health before explain_context. Treat evidence as a locator and verify cited live files."));
    }
    if(method.equals("notifications/initialized")){initialized=true;return null;}
    if(method.equals("ping"))return result(id,Map.of());
    if(!initialized)return error(id,-32002,"Server has not received notifications/initialized");
    if(method.equals("tools/list"))return result(id,Map.of("tools",toolDefinitions()));
    if(method.equals("tools/call"))return callTool(id,request.path("params"));
    return error(id,-32601,"Method not found: "+method);
  }

  private Map<String,Object> callTool(JsonNode id,JsonNode params)throws Exception{
    String name=text(params,"name");JsonNode arguments=params.path("arguments");
    if(name==null)return error(id,-32602,"Tool name is required");
    try(var store=new SqliteKnowledgeStore(db)){
      store.initialize();var status=new IndexStatusService().inspect(store);
      if(name.equals("health"))return toolResult(id,status,false);
      if(name.equals("explain_context")){
        if(!status.ready())return toolResult(id,Map.of(
          "code","STALE_INDEX","message","Index is empty, stale, or cannot be verified.","status",status),true);
        String question=text(arguments,"question");int limit=arguments.has("limit")?arguments.path("limit").asInt():25;
        return toolResult(id,new ContextQueryService(store).explain(question,limit),false);
      }
      return error(id,-32602,"Unknown tool: "+name);
    }
  }

  private List<Map<String,Object>> toolDefinitions(){
    return List.of(
      Map.of("name","health","title","Engineering index health",
        "description","Check whether indexed repositories still match their live Git HEAD.",
        "inputSchema",Map.of("type","object","properties",Map.of(),"additionalProperties",false),
        "annotations",Map.of("readOnlyHint",true,"destructiveHint",false)),
      Map.of("name","explain_context","title","Explain current engineering context",
        "description","Retrieve bounded current-code evidence and deterministic relationships for an engineering question. Verify cited files before making behavioral claims.",
        "inputSchema",Map.of("type","object","properties",Map.of(
          "question",Map.of("type","string","description","Question about the indexed current code."),
          "limit",Map.of("type","integer","minimum",1,"maximum",100,"default",25)),
          "required",List.of("question"),"additionalProperties",false),
        "annotations",Map.of("readOnlyHint",true,"destructiveHint",false)));
  }

  private Map<String,Object> toolResult(JsonNode id,Object value,boolean isError)throws JsonProcessingException{
    Object structured=mapper.convertValue(value,Object.class);String text=mapper.writeValueAsString(value);
    return result(id,Map.of("content",List.of(Map.of("type","text","text",text)),
      "structuredContent",structured,"isError",isError));
  }
  private Map<String,Object> result(JsonNode id,Object value){
    var response=new LinkedHashMap<String,Object>();response.put("jsonrpc","2.0");response.put("id",id);
    response.put("result",value);return response;
  }
  private Map<String,Object> error(JsonNode id,int code,String message){
    var detail=new LinkedHashMap<String,Object>();detail.put("code",code);detail.put("message",message);
    var response=new LinkedHashMap<String,Object>();response.put("jsonrpc","2.0");response.put("id",id);
    response.put("error",detail);return response;
  }
  private String text(JsonNode node,String field){
    if(node==null||!node.hasNonNull(field)||!node.path(field).isValueNode())return null;
    return node.path(field).asText();
  }
}
