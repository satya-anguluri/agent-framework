# Agent Framework

A provenance-first engineering context system for Jira-driven changes spanning **Mapper, Applier, Deployer, Deployment Manager**, and their shared database.

This is not a code-generation bot and it does not copy repositories into one prompt. It builds a focused, queryable knowledge index, then requires the agent to inspect current source before changing anything.

## MVP capabilities

- Index multiple Git repositories at a specific commit
- Extract Java types, Spring endpoints, JPA table mappings, and Flyway/Liquibase SQL objects
- Store cross-repository relationships and architecture decisions
- Search with SQLite FTS5
- Return source path, repository, and commit provenance with every result
- Keep Jira work in a gated workflow: context → blast radius → plan → current-code verification → implementation → validation

## Quick start

```bash
cp config/repositories.example.json config/repositories.json
# Edit each localPath to point to an existing local checkout.
mvn test
mvn package
java -jar target/agent-framework-0.1.0-SNAPSHOT.jar init --db .agent/context.db
java -jar target/agent-framework-0.1.0-SNAPSHOT.jar index --db .agent/context.db --config config/repositories.json
java -jar target/agent-framework-0.1.0-SNAPSHOT.jar search --db .agent/context.db "rollback deployment status"
```

The framework intentionally reads local checkouts. Authentication and cloning stay outside the indexer, preventing credentials from entering the knowledge database.

See [docs/architecture.md](docs/architecture.md) and [AGENTS.md](AGENTS.md).
