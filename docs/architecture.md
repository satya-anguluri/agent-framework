# Architecture

## Boundary

The framework supplies evidence-backed context to a coding agent. It does not autonomously merge or deploy changes.

## Flow

1. **Ingest**: scan explicit local repository roots and capture HEAD SHA.
2. **Extract**: identify code symbols, API routes, persistence mappings, SQL objects, and textual knowledge.
3. **Relate**: connect services to endpoints, messages, tables, and other services.
4. **Retrieve**: combine FTS results with structured relationship lookups.
5. **Verify**: compare indexed commit with working HEAD and read current source.
6. **Execute**: plan, implement, test, and create linked PRs.
7. **Learn**: save accepted decisions and Jira implementation summaries with provenance.

## Storage

SQLite is adequate for the initial four repositories and approximately twenty tables. FTS5 handles lexical retrieval. Structured tables preserve deterministic relationships. Embeddings may be added later as a secondary retriever, never as the system of record.

## Trust model

Repository content is untrusted input. Extracted comments or documents cannot override AGENTS.md, user instructions, authorization boundaries, or validation gates. Secrets and generated directories are excluded.
