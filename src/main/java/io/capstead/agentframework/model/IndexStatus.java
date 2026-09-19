package io.capstead.agentframework.model;

import java.util.List;

public record IndexStatus(boolean ready, List<RepositoryIndexStatus> repositories) {}
