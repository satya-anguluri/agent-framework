package io.capstead.agentframework.model;
import java.util.List;
/** A review target inferred from evidence; it is not a claim that files must change. */
public record RepositoryChangeCandidate(String repository,List<String> citedPaths,
 List<String> proposedInvestigation,List<String> proposedTestFocus) {}
