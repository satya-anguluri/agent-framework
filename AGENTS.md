# Agent Framework Instructions

## Purpose
Build a reusable cross-repository context layer for any software project. Repository names, issue prefixes, languages, transports, and database technologies must not be hard-coded into the core.

## Rules
1. Live repositories are authoritative; the index is only an evidence locator.
2. Every claim carries repository, path, and revision provenance.
3. Refuse stale evidence and dirty-worktree indexing.
4. Never index secrets, generated output, credentials, or runtime configuration values.
5. Evidence is not diagnosis; observed facts stay separate from inferred impact.
6. No automatic production deployment.

## Extensibility
- Language/framework extraction belongs behind `SourceArtifactExtractor`.
- Built-ins are adapters, not core assumptions.
- Discover additional extractors with Java `ServiceLoader`.
- Project topology comes only from configuration and deterministic evidence.
- Jira is the first work-item adapter; repository and history features remain tracker-neutral.
