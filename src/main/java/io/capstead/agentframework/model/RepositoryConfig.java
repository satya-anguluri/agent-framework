package io.capstead.agentframework.model;

import java.util.List;

public record RepositoryConfig(List<RepositorySpec> repositories) {
    public record RepositorySpec(String name, String localPath, String defaultBranch) {}
}
