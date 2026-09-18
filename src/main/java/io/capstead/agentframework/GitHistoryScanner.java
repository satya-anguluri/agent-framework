package io.capstead.agentframework;

import io.capstead.agentframework.model.HistoryCommit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;

final class GitHistoryScanner {
 private static final Pattern JIRA=Pattern.compile("\\b[A-Z][A-Z0-9]+-\\d+\\b");

 List<HistoryCommit> scan(Path root)throws IOException,InterruptedException{
  Process process=new ProcessBuilder("git","-C",root.toString(),"log","--all","--date=iso-strict",
   "--pretty=format:%x1e%H%x1f%aI%x1f%s","--name-only").redirectErrorStream(true).start();
  String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
  if(process.waitFor()!=0)throw new IOException("Cannot read Git history for "+root+": "+output);
  return parse(output);
 }

 List<HistoryCommit> parse(String output){
  List<HistoryCommit> commits=new ArrayList<>();
  for(String raw:output.split("\\u001e")){
   String record=raw.strip();
   if(record.isEmpty())continue;
   String[] lines=record.split("\\R");
   String[] header=lines[0].split("\\u001f",3);
   if(header.length!=3||!header[0].matches("[0-9a-fA-F]{40}"))continue;
   LinkedHashSet<String> keys=new LinkedHashSet<>();
   Matcher matcher=JIRA.matcher(header[2].toUpperCase(Locale.ROOT));
   while(matcher.find())keys.add(matcher.group());
   if(keys.isEmpty())continue;
   LinkedHashSet<String> files=new LinkedHashSet<>();
   for(int i=1;i<lines.length;i++)if(!lines[i].isBlank())files.add(lines[i].trim());
   commits.add(new HistoryCommit(header[0],header[1],header[2],List.copyOf(files),Set.copyOf(keys)));
  }
  return commits;
 }
}
