# Compatibility and support policy

## Versioning

Agent Framework follows Semantic Versioning.

- Before 1.0, minor releases may contain intentional API or schema changes documented in release notes.
- Patch releases contain compatible fixes.
- From 1.0 onward, incompatible public API or persisted-schema changes require a major release.

Git tags use `vMAJOR.MINOR.PATCH`; Maven artifacts use `MAJOR.MINOR.PATCH`.

## Supported environment

| Component | Policy |
|---|---|
| Java | Java 21 is the build and minimum supported runtime |
| Git | A maintained Git version supporting `diff --name-status` and `rev-parse --verify` |
| Database | SQLite through the bundled Xerial JDBC driver |
| Operating systems | Linux and macOS; Windows is supported through a Java 21 runtime and Git-compatible paths |
| Work-item sources | Canonical JSON, GitHub Issues, Jira REST, and Linear GraphQL payloads |
| Repository languages | Core extraction is strongest for Java/Spring plus generic delivery/configuration artifacts; other languages use extension SPIs |

## Release support

Until 1.0, the latest minor release receives fixes. Security fixes may be backported when practical, but no long-term-support line is promised. Release notes identify migrations and compatibility risks.

## Upgrade policy

1. Back up the SQLite database and generated artifacts.
2. Verify the downloaded SHA-256 checksum.
3. Run the packaged smoke test or `mvn clean verify`.
4. Re-run repository indexing after an upgrade when release notes change extraction or schema behavior.
5. Do not assume database downgrade compatibility.

## Security reporting

Do not open a public issue containing credentials, proprietary source, or secret-bearing evidence. Open a minimal issue without sensitive data and coordinate a private reproduction with the repository owner.
