# Agent Framework

A provenance-first engineering context and dependency framework for changes spanning any number of repositories.

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
- Index complete Git history as lightweight Jira→commit→changed-file relationships
- Link historical changed files to current classes, endpoints, tables, and documents
- Follow Git rename chains so older paths resolve to current source locations
- Retrieve historical Jira context from a new requirement or code concept
- Extend languages and frameworks through a ServiceLoader extractor SPI
- Resolve deterministic HTTP-service and messaging dependencies
- Understand Helm, application configuration, Vault references, Jenkinsfiles, and common CI/CD pipelines
- Import tracker-neutral work items and generate a consolidated evidence report

Full historical Jira bodies are intentionally not preloaded. Git history stores lightweight Jira links, and older Jira details can be hydrated on demand. Direct Jira API synchronization and architecture-decision management are planned next. The current import contract is intentionally connector-neutral.


## Bash quick start

Run these commands from the `agent-framework` repository root.

### 1. Build and verify

```bash
mvn --batch-mode clean verify

AF_JAR="target/agent-framework-0.1.0-SNAPSHOT.jar"
AF_DB=".agent/context.db"
AF_CONFIG="config/repositories.json"
```

### 2. Configure project repositories

```bash
cp config/repositories.example.json "$AF_CONFIG"

# Add any number of repositories and edit each localPath.
${EDITOR:-vi} "$AF_CONFIG"
```

Each configured repository must be a clean local Git checkout. The framework rejects modified or untracked files during repository indexing so the stored commit provenance remains accurate.

### 3. Initialize and index current code

```bash
java -jar "$AF_JAR" init \
  --db "$AF_DB"

java -jar "$AF_JAR" index \
  --db "$AF_DB" \
  --config "$AF_CONFIG"
```

Re-run `index` whenever one of the repositories advances to a new commit. Search and blast-radius commands refuse to use stale repository evidence.

### 4. Index Jira-linked Git history

Run this after current-code indexing because history records are linked to the indexed repositories and source files. Git rename detection preserves the original path and resolves it through rename chains to the current path.

```bash
java -jar "$AF_JAR" history-index \
  --db "$AF_DB" \
  --config "$AF_CONFIG"
```

### 5. Inspect cross-repository dependencies

```bash
java -jar "$AF_JAR" dependencies \
  --db "$AF_DB"
```

Only deterministic matches become edges; unresolved observations are retained as evidence.

### 6. Search current repository knowledge

```bash
java -jar "$AF_JAR" search \
  --db "$AF_DB" \
  "rollback deployment status"

java -jar "$AF_JAR" blast-radius \
  --db "$AF_DB" \
  "deployment"
```

### 7. Inspect historical Jira relationships

```bash
java -jar "$AF_JAR" jira-history \
  --db "$AF_DB" \
  FHB-1234

java -jar "$AF_JAR" related-jiras \
  --db "$AF_DB" \
  "mapper validation status"
```

`jira-history` starts with a known Jira key. `related-jiras` starts with a new requirement or code concept and follows current code → source file → historical commit → Jira key.

### 8. Import a current Jira work item

Copy and edit the connector-neutral example:

```bash
cp examples/jira-work-item.json /tmp/FHB-1234.json
${EDITOR:-vi} /tmp/FHB-1234.json

java -jar "$AF_JAR" jira-import \
  --db "$AF_DB" \
  --file /tmp/FHB-1234.json

java -jar "$AF_JAR" jira-show \
  --db "$AF_DB" \
  FHB-1234
```

### 9. Generate the Jira-driven blast radius

```bash
java -jar "$AF_JAR" jira-blast-radius \
  --db "$AF_DB" \
  FHB-1234
```

The report separates observed repository evidence from possible impact. The agent must still inspect current source, tests, API/message contracts, database compatibility, and rollout order before implementation.

## Refresh after repository changes

```bash
mvn --batch-mode verify

java -jar "$AF_JAR" index \
  --db "$AF_DB" \
  --config "$AF_CONFIG"

java -jar "$AF_JAR" history-index \
  --db "$AF_DB" \
  --config "$AF_CONFIG"
```

Authentication and cloning stay outside the indexer, preventing credentials from entering the knowledge database.

See [docs/architecture.md](docs/architecture.md) and [AGENTS.md](AGENTS.md).


## Deployment and delivery metadata

Built-in structural extractors understand Helm charts, Kubernetes templates, Spring application configuration, Vault HCL/policy references, Jenkinsfiles, GitHub Actions, Azure Pipelines, and GitLab CI. They retain identifiers such as configuration keys, Helm value references, resource kinds, Vault paths, pipeline stages, downstream jobs, actions, tasks, and templates.

They deliberately do not retain configuration values, placeholder defaults, shell command bodies, tokens, passwords, or credential contents.


## Analyze a work item

The normalized contract supports Jira, Linear, GitHub Issues, and internal trackers.

```bash
cp examples/work-item.json /tmp/PROJECT-1234.json
${EDITOR:-vi} /tmp/PROJECT-1234.json
java -jar "$AF_JAR" work-item-import --db "$AF_DB" --file /tmp/PROJECT-1234.json

java -jar "$AF_JAR" analyze-work-item \
  --db "$AF_DB" \
  --limit 50 \
  PROJECT-1234

java -jar "$AF_JAR" analyze-work-item \
  --db "$AF_DB" \
  --format json \
  PROJECT-1234 > /tmp/PROJECT-1234-analysis.json
```

The deterministic report groups observed evidence into code, APIs/messages, data, Helm/Kubernetes, application configuration, Vault, CI/CD, tests, and other evidence. Every observation retains repository, path, line, and commit provenance. Resolved cross-repository dependencies and optional historical Jira context are separate sections. Jira is named explicitly because Git history currently records Jira keys; other tracker-history providers can be added independently.

Required verification and rollout/rollback checks are derived only from the evidence categories present. They remain checklists—not diagnoses or claims that a file must change.


## Import tracker payloads through adapters

The core remains tracker-neutral. Built-in adapters normalize canonical JSON, GitHub Issues, Jira REST issue responses, and Linear GraphQL issue responses into the same `WorkItem` contract. Fetching stays outside the framework so authentication tokens are never stored in the knowledge database.

GitHub:

```bash
curl --fail-with-body --silent --show-error \
  --header "Authorization: Bearer $GITHUB_TOKEN" \
  --header "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/acme/payments/issues/42" \
  --output /tmp/github-42.json

java -jar "$AF_JAR" work-item-import \
  --db "$AF_DB" \
  --adapter github \
  --source-uri "https://api.github.com/repos/acme/payments/issues/42" \
  --file /tmp/github-42.json
```

Jira:

```bash
curl --fail-with-body --silent --show-error \
  --user "$JIRA_EMAIL:$JIRA_API_TOKEN" \
  --header "Accept: application/json" \
  "$JIRA_BASE_URL/rest/api/3/issue/PAY-9" \
  --output /tmp/jira-PAY-9.json

java -jar "$AF_JAR" work-item-import \
  --db "$AF_DB" \
  --adapter jira \
  --adapter-option acceptanceField=customfield_10042 \
  --source-uri "$JIRA_BASE_URL/browse/PAY-9" \
  --file /tmp/jira-PAY-9.json
```

Linear:

```bash
curl --fail-with-body --silent --show-error \
  --request POST \
  --header "Authorization: $LINEAR_API_KEY" \
  --header "Content-Type: application/json" \
  --data '{"query":"query { issue(id: \"PAY-10\") { identifier title description state { name } url updatedAt } }"}' \
  "https://api.linear.app/graphql" \
  --output /tmp/linear-PAY-10.json

java -jar "$AF_JAR" work-item-import \
  --db "$AF_DB" \
  --adapter linear \
  --source-uri "https://linear.app/acme/issue/PAY-10" \
  --file /tmp/linear-PAY-10.json
```

Use the namespaced identifier when different trackers contain the same key:

```bash
java -jar "$AF_JAR" analyze-work-item --db "$AF_DB" "jira:PAY-9"
```

Jira custom-field IDs are instance-specific. Set `acceptanceField` to the field ID used by your Jira site; omit it when acceptance criteria are not mapped.

All stored provenance URLs have user information, query parameters, and fragments removed before persistence.

Additional trackers can implement `WorkItemAdapter` and register the implementation with Java `ServiceLoader`; no analysis-engine changes are required.


### Extending analysis categories

Custom extractors can also provide an `EvidenceCategoryResolver` through Java `ServiceLoader`. External resolvers run before the built-in mappings, allowing new artifact kinds to participate in verification and rollout sections without modifying the framework core.


## Export an implementation plan

After reviewing the unified analysis, export a proposed plan for human approval:

```bash
java -jar "$AF_JAR" plan-work-item \
  --db "$AF_DB" \
  --format markdown \
  --output /tmp/PROJECT-1234-plan.md \
  PROJECT-1234

java -jar "$AF_JAR" plan-work-item \
  --db "$AF_DB" \
  --format json \
  --output /tmp/PROJECT-1234-plan.json \
  PROJECT-1234
```

The plan keeps observed evidence separate from proposed repository investigations. It includes cited paths, test focus, dependency-order questions, assumptions, approval gates, and rollback requirements. Cited files are candidates for inspection—not claims that every file must change. The command refuses to plan from stale repository indexes.


## Validate an implementation

Index repositories at the intended implementation baseline, import the work item, and generate the plan. After the implementation is committed in each local repository, compare each indexed baseline with the requested head:

```bash
java -jar "$AF_JAR" validate-work-item \
  --db "$AF_DB" \
  --head HEAD \
  PROJECT-1234

java -jar "$AF_JAR" validate-work-item \
  --db "$AF_DB" \
  --head HEAD \
  --format json \
  PROJECT-1234 > /tmp/PROJECT-1234-validation.json
```

The command reports committed file changes with repository and base/head commit provenance. Renames preserve both baseline and destination paths. Test evidence is evaluated per repository using common Java, Go, Python, JavaScript, TypeScript, Ruby, and conventional test/spec directory patterns. Requirement checks use three states:

- `SATISFIED`: objective file evidence was found, but behavioral correctness is not proven.
- `MISSING`: expected evidence such as tests is absent and needs explanation or remediation.
- `UNVERIFIABLE`: source inspection, execution evidence, or human approval is required.

The decision is always `HUMAN_REVIEW_REQUIRED`. Validation never approves, merges, deploys, or edits the implementation. Uncommitted worktree changes are intentionally excluded from the Git comparison.


## Export a pull-request review artifact

Generate a review document after implementation validation:

```bash
java -jar "$AF_JAR" review-work-item \
  --db "$AF_DB" \
  --head HEAD \
  --format markdown \
  --output /tmp/PROJECT-1234-pr-review.md \
  PROJECT-1234

java -jar "$AF_JAR" review-work-item \
  --db "$AF_DB" \
  --head HEAD \
  --format json \
  --output /tmp/PROJECT-1234-pr-review.json \
  PROJECT-1234
```

The artifact includes the work-item provenance, status counts, committed-change table, satisfied checks, missing evidence, unverifiable requirements, approval gates, and validation limitations. Markdown fields are escaped before rendering.

The framework only writes the requested local artifact. It does not connect to a pull-request provider, post comments, approve, merge, deploy, or modify source code.
