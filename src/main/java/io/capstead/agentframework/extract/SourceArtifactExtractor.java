package io.capstead.agentframework.extract;
import io.capstead.agentframework.model.KnowledgeItem;
import java.nio.file.Path;
import java.util.List;
/** ServiceLoader extension point for language/framework-specific evidence extraction. */
public interface SourceArtifactExtractor {
 boolean supports(Path relativePath);
 List<KnowledgeItem> extract(Path relativePath,String sanitizedContent,String originalContent,String commitSha);
}
