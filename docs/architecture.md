# Architecture

Agent Framework is a project-neutral context and dependency engine.

## Layers
- **Core:** repositories, provenance, SQLite/FTS, dependency graph, history lineage, staleness gates.
- **Source adapters:** `SourceArtifactExtractor` implementations loaded through `ServiceLoader`.
- **Work-item adapters:** normalized work items; Jira is the first adapter.
- **Agent adapters:** any coding agent consumes the evidence contract.

The built-in adapters recognize Java/Spring dependencies plus Helm, Kubernetes, Vault, Jenkins, GitHub Actions, Azure Pipelines, and GitLab CI structure. Configuration values, secrets, tokens, and shell bodies are excluded. Other ecosystems can be added without changing core orchestration.

Only deterministic edges are resolved automatically. Unresolved observations remain evidence and are never presented as diagnosis.
