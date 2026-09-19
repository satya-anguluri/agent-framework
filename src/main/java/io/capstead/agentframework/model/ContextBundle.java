package io.capstead.agentframework.model;

import java.util.List;

public record ContextBundle(
  String question,
  List<String> queryTerms,
  List<AnalysisEvidence> observedEvidence,
  List<ContextRelationship> deterministicRelationships,
  List<String> limitations) {}
