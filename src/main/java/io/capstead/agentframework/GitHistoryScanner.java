package io.capstead.agentframework;

import io.capstead.agentframework.model.HistoryCommit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;

final class GitHistoryScanner{
 private static final Pattern JIRA=Pattern.compile("\\b[A-Z][A-Z0-9]+-\\d+\\b");
 private record RawCommit(String sha,String date,String subject,List<String> files,Set<String> keys){}

 List<HistoryCommit> scan(Path root)throws IOException,InterruptedException{
  Process process=new ProcessBuilder("git","-C",root.toString(),"log","--all","--date=iso-strict",
   "--pretty=format:%x1e%H%x1f%aI%x1f%s","--name-status","--find-renames")
   .redirectErrorStream(true).start();
  String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
  if(process.waitFor()!=0)throw new IOException("Cannot read Git history for "+root+": "+output);
  return parse(output);
 }

 List<HistoryCommit> parse(String output){
  List<RawCommit> rawCommits=new ArrayList<>();
  Map<String,String> renameAliases=new LinkedHashMap<>();
  for(String raw:output.split("\\u001e")){
   String record=raw.strip();if(record.isEmpty())continue;
   String[] lines=record.split("\\R");
   String[] header=lines[0].split("\\u001f",3);
   if(header.length!=3||!header[0].matches("[0-9a-fA-F]{40}"))continue;
   LinkedHashSet<String> keys=new LinkedHashSet<>();
   Matcher matcher=JIRA.matcher(header[2].toUpperCase(Locale.ROOT));
   while(matcher.find())keys.add(matcher.group());
   LinkedHashSet<String> files=new LinkedHashSet<>();
   for(int i=1;i<lines.length;i++){
    if(lines[i].isBlank())continue;
    String[] change=lines[i].split("\\t");
    if(change.length<2)continue;
    if(change[0].startsWith("R")&&change.length>=3){
     renameAliases.put(change[1],change[2]);files.add(change[2]);
    }else if(change[0].startsWith("C")&&change.length>=3){files.add(change[2]);}
    else files.add(change[1]);
   }
   if(!keys.isEmpty())rawCommits.add(new RawCommit(header[0],header[1],header[2],List.copyOf(files),Set.copyOf(keys)));
  }
  List<HistoryCommit> result=new ArrayList<>();
  for(RawCommit raw:rawCommits){
   Map<String,String> resolved=new LinkedHashMap<>();
   for(String path:raw.files())resolved.put(path,resolve(path,renameAliases));
   result.add(new HistoryCommit(raw.sha(),raw.date(),raw.subject(),raw.files(),raw.keys(),resolved));
  }
  return result;
 }

 private String resolve(String path,Map<String,String> aliases){
  String current=path;Set<String> visited=new HashSet<>();
  while(aliases.containsKey(current)&&visited.add(current))current=aliases.get(current);
  return current;
 }
}
