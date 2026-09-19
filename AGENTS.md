# Agent Framework Instructions

## Purpose

Build and operate a cross-repository context layer for Jira work affecting Mapper, Applier, Deployer, Deployment Manager, and shared database objects.

## Source-of-truth rules

1. The live repository at the requested branch/commit is authoritative.
2. The SQLite index is a locator and evidence cache, never a replacement for reading current code.
3. Every retrieved claim must carry repository, path, and commit SHA.
4. If indexed SHA differs from working HEAD, mark context stale and re-index before planning.
5. Never index secrets, build output, .git contents, credentials, or environment files.

## Required Jira workflow

1. Load Jira description and acceptance criteria.
2. Query the context index for related symbols, tables, APIs, messages, decisions, and previous work.
3. Produce a blast-radius report covering all repositories and database objects.
4. Read the current files and tests in every affected repository.
5. Present an implementation plan and unresolved assumptions.
6. Implement only after the plan is accepted unless explicitly authorized otherwise.
7. Add unit, integration, migration, and cross-service scenario tests as applicable.
8. Run each repository's validation plus cross-repository scenarios.
9. Create separate, linked PRs when more than one repository changes.
10. Record implementation summary and final commit SHAs back into the knowledge index.

## Non-negotiable design rules

- Evidence is not diagnosis.
- Observed facts must be separated from inferred impact.
- No automated production deployment.
- No Jira ticket is assumed to belong only to the repository where it was filed.
- Database compatibility and rollout order must be explicit.
- Destructive migrations require a rollback or forward-recovery plan.
