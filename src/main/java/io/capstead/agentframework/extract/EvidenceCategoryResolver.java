package io.capstead.agentframework.extract;

import io.capstead.agentframework.model.AnalysisCategory;
import java.util.Optional;

/** ServiceLoader extension point for mapping extracted artifact kinds to report categories. */
public interface EvidenceCategoryResolver {
    Optional<AnalysisCategory> classify(String kind,String sourcePath);
}
