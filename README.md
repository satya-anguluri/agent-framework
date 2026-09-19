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
- Answer natural-language engineering questions with bounded, provenance-backed context bundles
- Serve the same context through a generic newline-delimited JSON agent protocol

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

## Windows PowerShell quick start

Run these commands from the `agent-framework` repository root in PowerShell 7 or Windows PowerShell 5.1. Java 21+, Maven, and Git must be available on `PATH`. Every executable command is kept on one line so it can be copied and pasted directly.

### 1. Build and configure paths

```powershell
mvn --batch-mode clean verify

$AfJar = (Resolve-Path ".\target\agent-framework-0.1.0-SNAPSHOT.jar").Path
$AfDb = Join-Path $PWD ".agent\context.db"
$AfConfig = Join-Path $PWD "config\repositories.json"
```

Maven must also use Java 21. `java -version` can report Java 21 while Maven still follows an older `JAVA_HOME`:

```powershell
mvn -version
$Java21Home = ((java -XshowSettings:properties -version 2>&1 | Select-String "java.home =").Line -replace "^\s*java.home\s*=\s*", "").Trim()
$env:JAVA_HOME = $Java21Home
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -version
```

The final command must report Java 21 before running the build. To retain the corrected `JAVA_HOME` for new PowerShell windows:

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", $Java21Home, "User")
```

### 2. Configure and index repositories

```powershell
Copy-Item ".\config\repositories.example.json" $AfConfig
notepad $AfConfig

java -jar $AfJar init --db $AfDb
java -jar $AfJar index --db $AfDb --config $AfConfig
java -jar $AfJar history-index --db $AfDb --config $AfConfig
java -jar $AfJar dependencies --db $AfDb
```

Use absolute Windows paths in the JSON configuration. Backslashes must be escaped, for example `C:\\source\\order-service`, or use forward slashes such as `C:/source/order-service`.

Generate the configuration with PowerShell to avoid JSON errors such as `Unrecognized character escape 'U'`:

```powershell
$RepositorySource = (Resolve-Path (Read-Host "Enter the full path to the repository you want to index")).Path
$RepositoryName = Read-Host "Enter a stable name for this repository"
@{ repositories = @(@{ name = $RepositoryName; localPath = $RepositorySource; defaultBranch = "main" }) } | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 $AfConfig
```

#### Index an active repository safely

Indexing intentionally refuses a dirty worktree. If the repository has tracked or untracked changes, do not commit or stash them merely to run the framework. Create a separate clean Git worktree at the exact commit you want to index:

```powershell
$RepositorySource = (Resolve-Path (Read-Host "Enter the full path to the active repository")).Path
$RepositoryName = Read-Host "Enter a stable name for this repository"
$RepositoryIndex = Join-Path $env:USERPROFILE ("agent-framework-worktrees\" + $RepositoryName + "-index")
New-Item -ItemType Directory -Force (Split-Path $RepositoryIndex) | Out-Null
git -C $RepositorySource worktree add --detach $RepositoryIndex HEAD
git -C $RepositoryIndex status --short
@{ repositories = @(@{ name = $RepositoryName; localPath = $RepositoryIndex; defaultBranch = "main" }) } | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 $AfConfig
java -jar $AfJar index --db $AfDb --config $AfConfig
```

`git status --short` must produce no output. Keep this clean worktree available after indexing because search, analysis, and agent queries verify its current Git commit before returning evidence. Your original working directory and uncommitted changes remain untouched.

To refresh the clean worktree later, first ensure it is clean, move it to the desired commit, and re-index:

```powershell
git -C $RepositorySource fetch origin
git -C $RepositoryIndex status --short
git -C $RepositoryIndex checkout --detach origin/main
java -jar $AfJar index --db $AfDb --config $AfConfig
```

### 3. Search and explain current behavior

```powershell
java -jar $AfJar search --db $AfDb "rollback deployment status"
java -jar $AfJar blast-radius --db $AfDb "deployment"
java -jar $AfJar related-jiras --db $AfDb "mapper validation status"

java -jar $AfJar explain --db $AfDb --format json --limit 25 "How does preorder work currently?" | Set-Content -Encoding utf8 ".\preorder-context.json"
```

### 4. Import and analyze a work item

```powershell
Copy-Item ".\examples\work-item.json" ".\PROJECT-1234.json"
notepad ".\PROJECT-1234.json"

java -jar $AfJar work-item-import --db $AfDb --file ".\PROJECT-1234.json"

java -jar $AfJar analyze-work-item --db $AfDb --format json "PROJECT-1234" | Set-Content -Encoding utf8 ".\PROJECT-1234-analysis.json"

java -jar $AfJar plan-work-item --db $AfDb --format markdown --output ".\PROJECT-1234-plan.md" "PROJECT-1234"
```

Fetch a GitHub issue with `curl.exe` so PowerShell does not substitute its web-request alias:

```powershell
curl.exe --fail-with-body --location --header "Authorization: Bearer $env:GITHUB_TOKEN" --header "Accept: application/vnd.github+json" "https://api.github.com/repos/acme/payments/issues/42" --output ".\github-42.json"

java -jar $AfJar work-item-import --db $AfDb --adapter github --source-uri "https://api.github.com/repos/acme/payments/issues/42" --file ".\github-42.json"
```

### 5. Validate and review committed changes

```powershell
java -jar $AfJar validate-work-item --db $AfDb --head HEAD --format json "PROJECT-1234" | Set-Content -Encoding utf8 ".\PROJECT-1234-validation.json"

java -jar $AfJar review-work-item --db $AfDb --head HEAD --format markdown --output ".\PROJECT-1234-review.md" "PROJECT-1234"
```

### 6. Run the Windows packaged smoke test

```powershell
powershell -ExecutionPolicy Bypass -File ".\scripts\smoke-test.ps1" -JarPath $AfJar
```

The script uses temporary clean Git repositories and removes them afterward.

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

The artifact includes the work-item provenance, per-repository baseline/head comparison table (including repositories with no changed files), status counts, committed-change table, satisfied checks, missing evidence, unverifiable requirements, approval gates, and validation limitations. Validation requires clean worktrees so uncommitted changes cannot be silently omitted. Markdown fields are escaped before rendering.

The framework only writes the requested local artifact. It does not connect to a pull-request provider, post comments, approve, merge, deploy, or modify source code.


## Install a released executable

Set the desired version and download both the executable JAR and checksum from GitHub Releases:

```bash
AF_VERSION="0.1.0"
AF_RELEASE="https://github.com/satya-anguluri/agent-framework/releases/download/v$AF_VERSION"

mkdir -p "$HOME/.local/share/agent-framework"
curl --fail-with-body --location \
  --output "$HOME/.local/share/agent-framework/agent-framework-$AF_VERSION.jar" \
  "$AF_RELEASE/agent-framework-$AF_VERSION.jar"
curl --fail-with-body --location \
  --output "$HOME/.local/share/agent-framework/agent-framework-$AF_VERSION.jar.sha256" \
  "$AF_RELEASE/agent-framework-$AF_VERSION.jar.sha256"

cd "$HOME/.local/share/agent-framework"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum --check "agent-framework-$AF_VERSION.jar.sha256"
else
  shasum -a 256 --check "agent-framework-$AF_VERSION.jar.sha256"
fi
java -jar "agent-framework-$AF_VERSION.jar" --help
```

Upgrade by downloading a newer version beside the existing JAR, verifying its checksum, running `--help` and the project smoke test, backing up the SQLite database, and then updating your `AF_JAR` variable. Keep the previous verified JAR for rollback; database downgrade compatibility is not guaranteed.

## Consume the Maven package

Tagged releases publish `io.capstead:agent-framework:<version>` to GitHub Packages. Configure a GitHub Packages credential in Maven `settings.xml` under server ID `github`. Consumers must also declare the GitHub Packages repository because `distributionManagement` controls publishing only:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/satya-anguluri/agent-framework</url>
  </repository>
</repositories>

<dependency>
  <groupId>io.capstead</groupId>
  <artifactId>agent-framework</artifactId>
  <version>0.1.0</version>
</dependency>
```

The executable JAR remains the simplest installation for CLI usage.

Windows PowerShell release verification:

```powershell
$AfVersion = "0.1.0"
$Release = "https://github.com/satya-anguluri/agent-framework/releases/download/v$AfVersion"
$InstallDir = Join-Path $env:LOCALAPPDATA "AgentFramework"
New-Item -ItemType Directory -Force $InstallDir | Out-Null

curl.exe --fail-with-body --location --output (Join-Path $InstallDir "agent-framework-$AfVersion.jar") "$Release/agent-framework-$AfVersion.jar"
curl.exe --fail-with-body --location --output (Join-Path $InstallDir "agent-framework-$AfVersion.jar.sha256") "$Release/agent-framework-$AfVersion.jar.sha256"

$Expected = ((Get-Content (Join-Path $InstallDir "agent-framework-$AfVersion.jar.sha256")) -split '\s+')[0].ToLowerInvariant()
$Actual = (Get-FileHash -Algorithm SHA256 (Join-Path $InstallDir "agent-framework-$AfVersion.jar")).Hash.ToLowerInvariant()
if ($Expected -ne $Actual) { throw "Checksum verification failed." }
java -jar (Join-Path $InstallDir "agent-framework-$AfVersion.jar") --help
```

## Release process

Maintainers create a release by pushing a SemVer tag that points to a verified commit:

```bash
mvn --batch-mode clean verify
artifact="$(find target -maxdepth 1 -name 'agent-framework-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name 'original-*' -print -quit)"
bash scripts/smoke-test.sh "$artifact"

git tag -a v0.1.0 -m "Agent Framework 0.1.0"
git push origin v0.1.0
```

The release workflow derives the Maven version from the tag, verifies the project, runs the packaged smoke test, publishes to GitHub Packages, creates a SHA-256 checksum, and attaches both files to a GitHub Release.

See [configuration reference](docs/configuration.md) and [compatibility policy](docs/compatibility.md).

## Ask how the current system works

After indexing, retrieve a small evidence bundle without rescanning the repository:

```bash
java -jar "$AF_JAR" explain \
  --db "$AF_DB" \
  --format json \
  --limit 25 \
  "How does preorder work currently?" \
  > /tmp/preorder-context.json
```

The response contains ranked observed evidence, deterministic relationships, exact repository/path/line/commit provenance, and explicit limitations. It is context for an engineer or coding agent—not a generated diagnosis. The command refuses stale repository indexes.

For a monorepo acceptance test, confirm that the result ranks the preorder controller/service, persistence, messages, configuration, and relevant tests without presenting unrelated projects as mandatory changes. An agent should inspect only the cited current files before making a behavioral claim.

## Connect Hermes or another coding agent

`serve` exposes a project-neutral newline-delimited JSON protocol over standard input/output. Each request and response occupies exactly one line; stdout contains protocol responses only.

```bash
printf '%s\n' \
  '{"id":"health-1","method":"health"}' \
  '{"id":"explain-1","method":"explain","params":{"question":"How does preorder work currently?","limit":25}}' \
  | java -jar "$AF_JAR" serve --db "$AF_DB"
```

Supported methods:

- `health`: reports whether every indexed repository still matches its live Git `HEAD`.
- `explain`: returns the same bounded context bundle as `explain --format json`.

Agents must call `health` or handle `STALE_INDEX`, use the returned evidence as a locator, and open only the cited live files needed to verify the answer. The protocol never modifies repositories, trackers, pull requests, or deployments and does not accept credentials.

For MCP clients such as coding agents, configure the executable JAR as a local stdio server:

```json
{
  "mcpServers": {
    "engineering-context": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/agent-framework-0.1.0.jar",
        "mcp",
        "--db",
        "/absolute/path/.agent/context.db"
      ]
    }
  }
}
```

The MCP server implements protocol version `2025-06-18` over stdio and exposes two read-only tools: `health` and `explain_context`. The exact client configuration filename is agent-specific; the server itself contains no Hermes-, Claude-, IDE-, or repository-specific code.
