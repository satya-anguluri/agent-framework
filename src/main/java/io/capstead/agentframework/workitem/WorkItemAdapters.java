package io.capstead.agentframework.workitem;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class WorkItemAdapters {
    private final Map<String,WorkItemAdapter> adapters;

    public WorkItemAdapters() {
        List<WorkItemAdapter> available=new ArrayList<>(List.of(
          new JsonWorkItemAdapter(),new GithubWorkItemAdapter(),
          new JiraWorkItemAdapter(),new LinearWorkItemAdapter()));
        ServiceLoader.load(WorkItemAdapter.class).forEach(available::add);
        adapters=available.stream().collect(Collectors.toUnmodifiableMap(
          a->a.id().toLowerCase(Locale.ROOT),Function.identity(),
          (first,duplicate)->{throw new IllegalStateException("Duplicate work-item adapter: "+first.id());}));
    }

    public WorkItemAdapter require(String id) {
        WorkItemAdapter adapter=adapters.get(id.toLowerCase(Locale.ROOT));
        if(adapter==null)throw new IllegalArgumentException(
          "Unknown work-item adapter '"+id+"'. Available: "+String.join(", ",adapters.keySet()));
        return adapter;
    }

    public Set<String> ids(){return new TreeSet<>(adapters.keySet());}
}
