package io.capstead.agentframework.extract;

import io.capstead.agentframework.model.KnowledgeItem;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;

public final class HelmConfigurationExtractor implements SourceArtifactExtractor{
 private static final Pattern YAML_KEY=Pattern.compile("^(\\s*)([A-Za-z0-9_.-]+)\\s*:");
 private static final Pattern PROPERTY=Pattern.compile("^\\s*([A-Za-z0-9_.-]+)\\s*[=:]");
 private static final Pattern PLACEHOLDER=Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_.-]*)(?::[^}]*)?}");
 private static final Pattern VALUES_REF=Pattern.compile("\\.Values\\.([A-Za-z0-9_.-]+)");
 private static final Pattern KIND=Pattern.compile("(?m)^\\s*kind\\s*:\\s*([A-Za-z][A-Za-z0-9.-]*)\\s*$");
 private record Level(int indent,String key){}

 public boolean supports(Path path){
  String normalized=path.toString().replace('\\','/').toLowerCase(Locale.ROOT);
  String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
  return name.equals("chart.yaml")||name.equals("chart.yml")||name.startsWith("values")&&isYaml(name)||
    name.startsWith("application")&&(isYaml(name)||name.endsWith(".properties"))||
    normalized.contains("/templates/")&&(isYaml(name)||name.endsWith(".tpl"));
 }

 public List<KnowledgeItem> extract(Path path,String sanitized,String original,String commit){
  String source=path.toString().replace('\\','/');
  Map<String,KnowledgeItem> items=new LinkedHashMap<>();
  String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
  if(name.endsWith(".properties"))extractProperties(original,source,commit,items);
  else extractYamlKeys(original,source,commit,items);
  extractReferences(original,source,commit,items);
  Matcher kinds=KIND.matcher(original);
  while(kinds.find())add(items,"k8s-resource",kinds.group(1)+":"+source,"Kubernetes resource kind "+kinds.group(1),source,line(original,kinds.start()),commit);
  return List.copyOf(items.values());
 }

 private void extractProperties(String text,String source,String commit,Map<String,KnowledgeItem> out){
  String[] lines=text.split("\\R",-1);
  for(int i=0;i<lines.length;i++){
   if(lines[i].stripLeading().startsWith("#"))continue;
   Matcher m=PROPERTY.matcher(lines[i]);
   if(m.find())add(out,"config-key",m.group(1),"Configuration key "+m.group(1)+" (value redacted)",source,i+1,commit);
  }
 }

 private void extractYamlKeys(String text,String source,String commit,Map<String,KnowledgeItem> out){
  Deque<Level> stack=new ArrayDeque<>();String[] lines=text.split("\\R",-1);
  for(int i=0;i<lines.length;i++){
   String line=lines[i];if(line.stripLeading().startsWith("#")||line.contains("{{"))continue;
   Matcher m=YAML_KEY.matcher(line);if(!m.find())continue;
   int indent=m.group(1).length();String key=m.group(2);
   while(!stack.isEmpty()&&stack.peekLast().indent()>=indent)stack.removeLast();
   String prefix=stack.stream().map(Level::key).reduce((a,b)->a+"."+b).orElse("");
   String full=prefix.isEmpty()?key:prefix+"."+key;
   add(out,"config-key",full,"Configuration key "+full+" (value redacted)",source,i+1,commit);
   stack.addLast(new Level(indent,key));
  }
 }

 private void extractReferences(String text,String source,String commit,Map<String,KnowledgeItem> out){
  Matcher placeholders=PLACEHOLDER.matcher(text);
  while(placeholders.find())add(out,"config-reference",placeholders.group(1),
   "Configuration placeholder "+placeholders.group(1)+" (default/value redacted)",source,line(text,placeholders.start()),commit);
  Matcher values=VALUES_REF.matcher(text);
  while(values.find())add(out,"helm-value-reference",values.group(1),
   "Helm values reference "+values.group(1),source,line(text,values.start()),commit);
 }

 private void add(Map<String,KnowledgeItem> out,String kind,String name,String evidence,String source,int line,String commit){
  out.putIfAbsent(kind+"\u0000"+name+"\u0000"+source,new KnowledgeItem(kind,name,evidence,source,line,line,commit));
 }
 private int line(String text,int offset){return 1+(int)text.substring(0,offset).chars().filter(c->c=='\n').count();}
 private boolean isYaml(String name){return name.endsWith(".yaml")||name.endsWith(".yml");}
}
