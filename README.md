# Agent Framework

A provenance-first engineering context system for Jira-driven changes spanning **Mapper, Applier, Deployer, Deployment Manager**, and their shared database.

It builds a focused knowledge index, then requires the agent to inspect current source before changing anything. The index is a locator, never a replacement for live code.

## Current MVP

- Index multiple clean Git worktrees at a specific commit
- Extract Java types, Spring endpoints, JPA table mappings, and Flyway/Liquibase SQL objects
- Exclude runtime configuration and common secret-bearing files
- Store repository, source path, line, and commit provenance
- Reject stale search results after a checkout advances
- Derive deterministic cross-repository relationships for shared tables/routes
- Produce a blast-radius evidence report with facts separated from possible impact
- Search using SQLite FTS5
- Import normalized Jira work items with source URL/update provenance
- Generate a Jira-driven blast-radius evidence report

Direct Jira API synchronization and architecture-decision management are planned next. The current import contract is intentionally connector-neutral.

## Quick start

```bash
cp config/repositories.example.json config/repositories.json
# Point localPath at clean local checkouts.
mvn verify
mvn package
java -jar target/agent-framework-0.1.0-SNAPSHOT.jar init --db .agent/context.db
java -jar target/agent-framework-0.1.0-SNAPSHOT.jar index --db .agent/context.db --config config/repositories.json
java -jar target/agent-framework-0.1.0-SNAPSHOT.jar search --db .agent/context.db "rollback deployment status"
java -jar target/agent-framework-0.1.0-SNAPSHOT.jar blast-radius --db .agent/context.db "deployment"
java -jar target/agent-framework-0.1.0-SNAPSHOT.jar jira-import --db .agent/context.db --file examples/jira-work-item.json
java -jar target/agent-framework-0.1.0-SNAPSHOT.jar jira-blast-radius --db .agent/context.db FHB-1234
```

Authentication and cloning stay outside the indexer, preventing credentials from entering the knowledge database.

See [docs/architecture.md](docs/architecture.md) and [AGENTS.md](AGENTS.md).
