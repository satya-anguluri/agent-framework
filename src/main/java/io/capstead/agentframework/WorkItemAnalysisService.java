package io.capstead.agentframework;

import io.capstead.agentframework.model.*;
import java.sql.SQLException;
import java.util.*;

final class WorkItemAnalysisService {
    private final SqliteKnowledgeStore store;
    WorkItemAnalysisService(SqliteKnowledgeStore store){this.store=store;}

    UnifiedAnalysisReport analyze(String key,int limit)throws SQLException{
        if(limit<1)throw new IllegalArgumentException("limit must be positive");
        WorkItem item=store.workItem(key).orElseThrow(()->new IllegalArgumentException("Unknown work item: "+key));
        List<AnalysisEvidence> evidence=store.workItemEvidenceDetails(key,limit);
        EnumMap<AnalysisCategory,List<AnalysisEvidence>> grouped=new EnumMap<>(AnalysisCategory.class);
        for(AnalysisEvidence row:evidence)grouped.computeIfAbsent(row.category(),ignored->new ArrayList<>()).add(row);
        Map<AnalysisCategory,List<AnalysisEvidence>> immutable=new LinkedHashMap<>();
        for(AnalysisCategory category:AnalysisCategory.values()){
            List<AnalysisEvidence> rows=grouped.get(category);
            if(rows!=null&&!rows.isEmpty())immutable.put(category,List.copyOf(rows));
        }
        List<String> dependencies=List.copyOf(store.workItemDependencies(key,limit));
        List<String> jiraHistory=List.copyOf(store.relatedWorkItems(key,limit));
        return new UnifiedAnalysisReport(item,Collections.unmodifiableMap(immutable),dependencies,jiraHistory,
          verification(immutable,dependencies),rollout(immutable,dependencies));
    }

    private static List<String> verification(Map<AnalysisCategory,List<AnalysisEvidence>> groups,List<String> dependencies){
        List<String> checks=new ArrayList<>();
        checks.add("Confirm every cited path and commit still implements the intended behavior.");
        checks.add("Trace acceptance criteria to current tests; add or update tests for uncovered behavior.");
        if(groups.containsKey(AnalysisCategory.API_AND_MESSAGES))
            checks.add("Verify API/message schema compatibility, producers, consumers, clients, retries, and idempotency.");
        if(groups.containsKey(AnalysisCategory.DATA))
            checks.add("Verify migration ordering, backward compatibility, data repair, and database rollback constraints.");
        if(groups.containsKey(AnalysisCategory.HELM_AND_KUBERNETES))
            checks.add("Render Helm/Kubernetes templates for each environment and validate manifests before deployment.");
        if(groups.containsKey(AnalysisCategory.APPLICATION_CONFIGURATION))
            checks.add("Verify configuration keys, defaults, overrides, and environment-specific ownership.");
        if(groups.containsKey(AnalysisCategory.VAULT))
            checks.add("Verify Vault paths, policies, mounts, and runtime access without exposing secret values.");
        if(groups.containsKey(AnalysisCategory.CI_CD))
            checks.add("Verify pipeline stages, quality gates, artifacts, promotions, and deployment dependencies.");
        if(!dependencies.isEmpty())
            checks.add("Confirm cross-repository owners and compatibility expectations for every resolved dependency.");
        checks.add("Resolve unmatched requirements as assumptions; this report is evidence, not diagnosis.");
        return List.copyOf(checks);
    }

    private static List<String> rollout(Map<AnalysisCategory,List<AnalysisEvidence>> groups,List<String> dependencies){
        List<String> checks=new ArrayList<>();
        if(!dependencies.isEmpty())checks.add("Define producer/consumer and upstream/downstream deployment order.");
        if(groups.containsKey(AnalysisCategory.DATA))checks.add("Use an expand/contract data rollout and document irreversible steps.");
        if(groups.containsKey(AnalysisCategory.HELM_AND_KUBERNETES)||groups.containsKey(AnalysisCategory.APPLICATION_CONFIGURATION))
            checks.add("Diff rendered configuration per environment and retain the prior deployable revision.");
        if(groups.containsKey(AnalysisCategory.VAULT))checks.add("Stage secret/policy changes before consumers and define revocation rollback.");
        if(groups.containsKey(AnalysisCategory.CI_CD))checks.add("Exercise deployment and rollback paths through the same pipeline controls.");
        checks.add("Define health signals, observation window, abort threshold, and responsible owner.");
        checks.add("Document rollback order and compatibility limits before implementation begins.");
        return List.copyOf(checks);
    }
}
