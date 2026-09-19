package io.capstead.agentframework.extract;
import io.capstead.agentframework.model.KnowledgeItem;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;
public final class JavaSpringDependencyExtractor implements SourceArtifactExtractor{
 private record Rule(String kind,String protocol,Pattern pattern){}
 private static final List<Rule> RULES=List.of(
  new Rule("service-client","http",Pattern.compile("@FeignClient\\s*\\([^)]*(?:name|value)\\s*=\\s*\"([^\"]+)\"")),
  new Rule("message-consumer","kafka",Pattern.compile("@KafkaListener\\s*\\([^)]*topics\\s*=\\s*\"([^\"]+)\"")),
  new Rule("message-consumer","sqs",Pattern.compile("@SqsListener\\s*\\(\\s*\"([^\"]+)\"")),
  new Rule("message-consumer","azure-service-bus",Pattern.compile("@(?:ServiceBusListener|ServiceBusProcessor)\\s*\\(\\s*\"([^\"]+)\"")),
  new Rule("message-producer","kafka",Pattern.compile("\\b(?:kafkaTemplate|KafkaTemplate)\\s*\\.\\s*send\\s*\\(\\s*\"([^\"]+)\"")),
  new Rule("message-producer","sqs",Pattern.compile("\\b(?:sqsTemplate|SqsTemplate)\\s*\\.\\s*send\\s*\\(\\s*\"([^\"]+)\"")),
  new Rule("http-client","http",Pattern.compile("\\.(?:uri|getForObject|postForObject)\\s*\\(\\s*\"([^\"]+)\""))
 );
 public boolean supports(Path path){return path.toString().endsWith(".java");}
 public List<KnowledgeItem> extract(Path path,String sanitized,String original,String commit){
  List<KnowledgeItem> found=new ArrayList<>();String source=path.toString().replace('\\','/');
  for(Rule rule:RULES){Matcher matcher=rule.pattern().matcher(sanitized);while(matcher.find()){
   String artifact=matcher.group(1).trim();int line=1+(int)original.substring(0,matcher.start()).chars().filter(c->c=='\n').count();
   int from=Math.max(0,matcher.start()-120),to=Math.min(original.length(),matcher.end()+180);
   found.add(new KnowledgeItem(rule.kind(),rule.protocol()+":"+artifact,original.substring(from,to),source,line,line,commit));
  }}return found;
 }
}
