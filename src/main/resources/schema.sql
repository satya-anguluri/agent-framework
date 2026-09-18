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
CREATE TRIGGER IF NOT EXISTS knowledge_ai AFTER INSERT ON knowledge BEGIN
 INSERT INTO knowledge_fts(rowid,name,content,kind,source_path) VALUES(new.id,new.name,new.content,new.kind,new.source_path);
END;
CREATE TRIGGER IF NOT EXISTS knowledge_ad AFTER DELETE ON knowledge BEGIN
 INSERT INTO knowledge_fts(knowledge_fts,rowid,name,content,kind,source_path) VALUES('delete',old.id,old.name,old.content,old.kind,old.source_path);
END;
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
