package io.capstead.agentframework;

import io.capstead.agentframework.model.IndexStatus;
import io.capstead.agentframework.model.RepositoryIndexStatus;
import java.util.ArrayList;
import java.util.List;

final class IndexStatusService {
  IndexStatus inspect(SqliteKnowledgeStore store) throws Exception {
    var statuses=new ArrayList<RepositoryIndexStatus>();
    for(var state:store.repositoryStates()){
      try{
        String current=GitSupport.head(state.root());
        boolean matches=current.equals(state.indexedCommit());
        statuses.add(new RepositoryIndexStatus(state.name(),state.indexedCommit(),current,matches,
          matches?"Index matches the live repository HEAD.":"Re-index before using this evidence."));
      }catch(Exception e){
        statuses.add(new RepositoryIndexStatus(state.name(),state.indexedCommit(),null,false,
          "Unable to verify the live repository HEAD; confirm the configured checkout is available."));
      }
    }
    return new IndexStatus(!statuses.isEmpty()&&statuses.stream().allMatch(RepositoryIndexStatus::current),
      List.copyOf(statuses));
  }
}
