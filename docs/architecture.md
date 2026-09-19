# Architecture

Agent Framework is a project-neutral context and dependency engine.

## Layers
- **Core:** repositories, provenance, SQLite/FTS, dependency graph, history lineage, staleness gates.
- **Source adapters:** `SourceArtifactExtractor` implementations loaded through `ServiceLoader`.
- **Work-item adapters:** normalized work items; Jira is the first adapter.
- **Agent adapters:** any coding agent consumes the evidence contract.

The built-in Java/Spring adapter recognizes common HTTP, Feign, Kafka, SQS, Azure Service Bus, JPA, and SQL patterns. Other ecosystems can be added without changing core orchestration.

Only deterministic edges are resolved automatically. Unresolved observations remain evidence and are never presented as diagnosis.
