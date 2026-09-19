package io.capstead.agentframework.extract;

import io.capstead.agentframework.model.KnowledgeItem;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;

public final class CiCdVaultExtractor implements SourceArtifactExtractor{
 private record Rule(String kind,Pattern pattern,String evidence){}
 private static final List<Rule> RULES=List.of(
  new Rule("pipeline-stage",Pattern.compile("\\bstage\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)"),"Jenkins stage"),
  new Rule("pipeline-job",Pattern.compile("\\bbuild\\s+(?:job\\s*:\\s*)?['\"]([^'\"]+)['\"]"),"Jenkins downstream job"),
  new Rule("pipeline-action",Pattern.compile("(?m)^\\s*-?\\s*uses\\s*:\\s*([^#\\s]+)"),"GitHub Actions dependency"),
  new Rule("pipeline-template",Pattern.compile("(?m)^\\s*-?\\s*template\\s*:\\s*([^#\\s]+)"),"Pipeline template"),
  new Rule("pipeline-task",Pattern.compile("(?m)^\\s*-?\\s*task\\s*:\\s*([^#\\s]+)"),"Pipeline task"),
  new Rule("vault-path",Pattern.compile("\\bpath\\s+[\"]([^\"]+)[\"]"),"Vault policy path"),
  new Rule("vault-reference",Pattern.compile("vault\\.hashicorp\\.com/(?:agent-inject-secret|role)-[^:]*:\\s*['\"]?([^'\"#\\s]+)"),"Vault Kubernetes reference")
 );
 public boolean supports(Path path){
  String p=path.toString().replace('\\','/').toLowerCase(Locale.ROOT);
  String n=path.getFileName().toString().toLowerCase(Locale.ROOT);
  return n.equals("jenkinsfile")||n.startsWith("jenkinsfile.")||n.equals("azure-pipelines.yml")||
   n.equals("azure-pipelines.yaml")||n.equals(".gitlab-ci.yml")||n.equals(".gitlab-ci.yaml")||
   p.contains("/.github/workflows/")&&(n.endsWith(".yml")||n.endsWith(".yaml"))||
   n.endsWith(".hcl")||n.endsWith(".vault-policy");
 }
 public List<KnowledgeItem> extract(Path path,String sanitized,String original,String commit){
  String source=path.toString().replace('\\','/');Map<String,KnowledgeItem> out=new LinkedHashMap<>();
  for(Rule rule:RULES){Matcher m=rule.pattern().matcher(original);while(m.find()){
   String name=m.group(1).trim();add(out,rule.kind(),name,rule.evidence()+" "+name,source,line(original,m.start()),commit);
  }}
  extractYamlNames(original,source,commit,out);
  return List.copyOf(out.values());
 }
 private void extractYamlNames(String text,String source,String commit,Map<String,KnowledgeItem> out){
  String[] lines=text.split("\\R",-1);
  for(int i=0;i<lines.length;i++){
   Matcher m=Pattern.compile("^\\s*(?:-\\s*)?(stage|job)\\s*:\\s*([A-Za-z0-9_.-]+)\\s*$").matcher(lines[i]);
   if(m.find())add(out,"pipeline-"+m.group(1),m.group(2),"Pipeline "+m.group(1)+" "+m.group(2),source,i+1,commit);
  }
 }
 private void add(Map<String,KnowledgeItem> out,String kind,String name,String evidence,String source,int line,String commit){
  if(name.contains("${{")||name.contains("$(")||name.length()>240)return;
  out.putIfAbsent(kind+"\u0000"+name+"\u0000"+source,new KnowledgeItem(kind,name,evidence,source,line,line,commit));
 }
 private int line(String text,int offset){return 1+(int)text.substring(0,offset).chars().filter(c->c=='\n').count();}
}
