package io.capstead.agentframework;

import io.capstead.agentframework.model.ContextBundle;
import java.sql.SQLException;
import java.util.List;

final class ContextQueryService {
  private final SqliteKnowledgeStore store;

  ContextQueryService(SqliteKnowledgeStore store){this.store=store;}

  ContextBundle explain(String question,int limit)throws SQLException{
    if(question==null||question.isBlank())throw new IllegalArgumentException("question must not be blank");
    if(limit<1||limit>100)throw new IllegalArgumentException("limit must be between 1 and 100");
    var query=store.questionQuery(question);
    var evidence=query.fts().isBlank()?List.<io.capstead.agentframework.model.AnalysisEvidence>of()
      :store.contextEvidence(query.fts(),limit);
    var relationships=evidence.isEmpty()?List.<io.capstead.agentframework.model.ContextRelationship>of()
      :store.contextRelationships(query.fts(),limit);
    var limitations=evidence.isEmpty()
      ?List.of("No indexed evidence matched the question; inspect current source or refine the question.",
        "The index is an evidence locator and does not prove runtime behavior.")
      :List.of("Observed evidence comes from the indexed commit; inspect cited live files before changing code.",
        "Relationships are included only when the index can derive them deterministically.",
        "Absence from this bounded bundle does not prove that no other relevant code exists.",
        "Runtime behavior, dynamic configuration, and external systems are not proven by static evidence.");
    return new ContextBundle(question,List.copyOf(query.terms()),evidence,relationships,limitations);
  }
}
