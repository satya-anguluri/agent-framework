# Configuration reference

## Runtime requirements

- Java 21 or newer within the supported compatibility range
- Git available on `PATH`
- Local, clean Git worktrees for every indexed repository
- A writable location for the SQLite knowledge database

## Repository configuration

The `index` and `history-index` commands accept a JSON file:

```json
{
  "repositories": [
    {
      "name": "order-service",
      "localPath": "/absolute/path/to/order-service",
      "defaultBranch": "main"
    }
  ]
}
```

| Field | Required | Meaning |
|---|---:|---|
| `name` | yes | Stable identifier used in evidence and dependency output |
| `localPath` | yes | Local Git worktree; use an absolute path for predictable automation |
| `defaultBranch` | yes | Informational default branch retained with repository provenance |

Indexing refuses dirty worktrees. Analysis commands refuse stale indexed evidence. Validation and review commands compare the indexed commit with the requested committed head and also require clean worktrees.

## Database

Pass the same database path to related commands:

```bash
AF_DB=".agent/context.db"
java -jar "$AF_JAR" init --db "$AF_DB"
```

The database contains extracted identifiers, sanitized snippets, work-item metadata, Git provenance, and relationships. It must not contain credentials or secret values. Back it up before upgrading and restrict filesystem access according to the sensitivity of repository metadata.

## Work-item adapters

Built-in adapter IDs are `json`, `github`, `jira`, and `linear`. Tracker authentication happens outside the framework. Use `--source-uri` for authoritative provenance and `--adapter-option key=value` for adapter-specific mappings.

Jira example:

```bash
java -jar "$AF_JAR" work-item-import \
  --db "$AF_DB" \
  --adapter jira \
  --adapter-option acceptanceField=customfield_10042 \
  --source-uri "$JIRA_BASE_URL/browse/PAY-9" \
  --file /tmp/PAY-9.json
```

## Output formats

- `analyze-work-item`: `text` or `json`
- `plan-work-item`: `markdown` or `json`
- `validate-work-item`: `text` or `json`
- `review-work-item`: `markdown` or `json`

Output files are replaced when `--output` is supplied. Review generated artifacts before sharing because repository paths and engineering metadata may be sensitive.

## Extension points

Java `ServiceLoader` extension points:

- `SourceArtifactExtractor`
- `EvidenceCategoryResolver`
- `WorkItemAdapter`

The shaded executable merges service descriptors so extensions remain discoverable when packaged correctly.

## Agent protocol

Run `serve --db <path>` to read newline-delimited JSON requests from stdin and write newline-delimited JSON responses to stdout. This transport is intentionally agent-neutral and can be wrapped by Hermes, Claude, an MCP server, or another local tool without adding agent-specific dependencies to the framework.

Request methods:

```json
{"id":"1","method":"health"}
{"id":"2","method":"explain","params":{"question":"How does preorder work currently?","limit":25}}
```

The `id` is echoed unchanged. Successful responses contain `ok: true` and `result`; failures contain `ok: false` and an `error` with a stable code and message. `explain` refuses requests when any configured repository has advanced beyond its indexed commit or cannot be inspected.

The limit must be between 1 and 100. Keep it small for agent prompts. Repository root paths and configuration values are not returned.

### MCP stdio

Run `mcp --db <path>` for MCP clients. The server implements the MCP `2025-06-18` initialization lifecycle plus `tools/list` and `tools/call`, with `health` and `explain_context` as read-only tools. Messages are newline-delimited JSON-RPC on stdin/stdout; nothing except valid MCP messages is written to stdout.
