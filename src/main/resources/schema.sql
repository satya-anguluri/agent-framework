PRAGMA foreign_keys=ON;
CREATE TABLE IF NOT EXISTS repositories (
 id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE, root_path TEXT NOT NULL,
 default_branch TEXT, indexed_commit TEXT NOT NULL, indexed_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS knowledge (
 id INTEGER PRIMARY KEY, repository_id INTEGER NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
 kind TEXT NOT NULL, name TEXT NOT NULL, content TEXT NOT NULL, source_path TEXT NOT NULL,
 line_start INTEGER, line_end INTEGER, commit_sha TEXT NOT NULL,
 UNIQUE(repository_id, kind, name, source_path, commit_sha)
);
CREATE VIRTUAL TABLE IF NOT EXISTS knowledge_fts USING fts5(
 name, content, kind, source_path, content='knowledge', content_rowid='id'
);
CREATE TABLE IF NOT EXISTS relationships (
 id INTEGER PRIMARY KEY, source_knowledge_id INTEGER NOT NULL REFERENCES knowledge(id) ON DELETE CASCADE,
 relation TEXT NOT NULL, target_kind TEXT NOT NULL, target_name TEXT NOT NULL,
 evidence TEXT, UNIQUE(source_knowledge_id, relation, target_kind, target_name)
);
CREATE TABLE IF NOT EXISTS decisions (
 id INTEGER PRIMARY KEY, title TEXT NOT NULL, decision TEXT NOT NULL, rationale TEXT,
 source TEXT NOT NULL, commit_sha TEXT, created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS jira_work (
 jira_key TEXT PRIMARY KEY, summary TEXT NOT NULL, description TEXT, acceptance_criteria TEXT,
 affected_repositories TEXT, implementation_summary TEXT, final_commits TEXT, updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS jira_sources (
 jira_key TEXT PRIMARY KEY REFERENCES jira_work(jira_key) ON DELETE CASCADE,
 source_url TEXT NOT NULL, source_updated_at TEXT, imported_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS history_commits (
 repository_id INTEGER NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
 commit_sha TEXT NOT NULL, committed_at TEXT NOT NULL, subject TEXT NOT NULL,
 PRIMARY KEY(repository_id,commit_sha)
);
CREATE TABLE IF NOT EXISTS commit_files (
 repository_id INTEGER NOT NULL, commit_sha TEXT NOT NULL, file_path TEXT NOT NULL,
 PRIMARY KEY(repository_id,commit_sha,file_path),
 FOREIGN KEY(repository_id,commit_sha) REFERENCES history_commits(repository_id,commit_sha) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS commit_jira (
 repository_id INTEGER NOT NULL, commit_sha TEXT NOT NULL, jira_key TEXT NOT NULL,
 PRIMARY KEY(repository_id,commit_sha,jira_key),
 FOREIGN KEY(repository_id,commit_sha) REFERENCES history_commits(repository_id,commit_sha) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_commit_jira_key ON commit_jira(jira_key);

CREATE TABLE IF NOT EXISTS commit_knowledge (
 repository_id INTEGER NOT NULL, commit_sha TEXT NOT NULL,
 knowledge_id INTEGER NOT NULL REFERENCES knowledge(id) ON DELETE CASCADE,
 PRIMARY KEY(repository_id,commit_sha,knowledge_id),
 FOREIGN KEY(repository_id,commit_sha) REFERENCES history_commits(repository_id,commit_sha) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_commit_knowledge_item ON commit_knowledge(knowledge_id);

CREATE TABLE IF NOT EXISTS commit_file_lineage (
 repository_id INTEGER NOT NULL, commit_sha TEXT NOT NULL,
 original_path TEXT NOT NULL, current_path TEXT NOT NULL,
 PRIMARY KEY(repository_id,commit_sha,original_path),
 FOREIGN KEY(repository_id,commit_sha) REFERENCES history_commits(repository_id,commit_sha) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_file_lineage_current ON commit_file_lineage(repository_id,current_path);

CREATE TABLE IF NOT EXISTS dependency_edges (
 id INTEGER PRIMARY KEY,
 source_repository_id INTEGER NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
 source_knowledge_id INTEGER NOT NULL REFERENCES knowledge(id) ON DELETE CASCADE,
 target_repository_id INTEGER REFERENCES repositories(id) ON DELETE CASCADE,
 target_knowledge_id INTEGER REFERENCES knowledge(id) ON DELETE CASCADE,
 dependency_type TEXT NOT NULL, artifact_name TEXT NOT NULL, evidence TEXT NOT NULL,
 UNIQUE(source_knowledge_id,target_repository_id,target_knowledge_id,dependency_type,artifact_name)
);
CREATE INDEX IF NOT EXISTS idx_dependency_source ON dependency_edges(source_repository_id);
CREATE INDEX IF NOT EXISTS idx_dependency_target ON dependency_edges(target_repository_id);

CREATE TABLE IF NOT EXISTS work_items (
 source_system TEXT NOT NULL, work_item_key TEXT NOT NULL, summary TEXT NOT NULL,
 description TEXT, acceptance_criteria TEXT, status TEXT, updated_at TEXT NOT NULL,
 PRIMARY KEY(source_system,work_item_key)
);
CREATE TABLE IF NOT EXISTS work_item_sources (
 source_system TEXT NOT NULL, work_item_key TEXT NOT NULL,
 source_url TEXT NOT NULL, source_updated_at TEXT, imported_at TEXT NOT NULL,
 PRIMARY KEY(source_system,work_item_key),
 FOREIGN KEY(source_system,work_item_key)
   REFERENCES work_items(source_system,work_item_key) ON DELETE CASCADE
);
