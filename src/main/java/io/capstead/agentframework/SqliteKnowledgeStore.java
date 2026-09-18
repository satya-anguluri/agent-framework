package io.capstead.agentframework;

import io.capstead.agentframework.model.KnowledgeItem;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

final class SqliteKnowledgeStore implements AutoCloseable {
    private final Connection connection;

    SqliteKnowledgeStore(Path db) throws SQLException, IOException {
        Path parent = db.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
        connection.createStatement().execute("PRAGMA foreign_keys=ON");
    }

    void initialize() throws SQLException, IOException {
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/schema.sql"))) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String statement : sql.split(";\\s*(?:\\R|$)")) {
                if (!statement.isBlank()) connection.createStatement().execute(statement);
            }
        }
    }

    void replaceRepository(String name, Path root, String branch, String commit, List<KnowledgeItem> items)
            throws SQLException {
        connection.setAutoCommit(false);
        try {
            long repoId;
            try (PreparedStatement up = connection.prepareStatement("""
                INSERT INTO repositories(name,root_path,default_branch,indexed_commit,indexed_at)
                VALUES(?,?,?,?,?) ON CONFLICT(name) DO UPDATE SET root_path=excluded.root_path,
                default_branch=excluded.default_branch,indexed_commit=excluded.indexed_commit,indexed_at=excluded.indexed_at
                """)) {
                up.setString(1,name); up.setString(2,root.toAbsolutePath().toString()); up.setString(3,branch);
                up.setString(4,commit); up.setString(5, Instant.now().toString()); up.executeUpdate();
            }
            try (PreparedStatement q=connection.prepareStatement("SELECT id FROM repositories WHERE name=?")) {
                q.setString(1,name); try(ResultSet rs=q.executeQuery()){ rs.next(); repoId=rs.getLong(1); }
            }
            try (PreparedStatement del=connection.prepareStatement("DELETE FROM knowledge WHERE repository_id=?")) {
                del.setLong(1,repoId); del.executeUpdate();
            }
            try (PreparedStatement in=connection.prepareStatement("""
                INSERT INTO knowledge(repository_id,kind,name,content,source_path,line_start,line_end,commit_sha)
                VALUES(?,?,?,?,?,?,?,?)""")) {
                for (KnowledgeItem i:items) {
                    in.setLong(1,repoId); in.setString(2,i.kind()); in.setString(3,i.name());
                    in.setString(4,i.content()); in.setString(5,i.sourcePath());
                    if(i.lineStart()==null) in.setNull(6,Types.INTEGER); else in.setInt(6,i.lineStart());
                    if(i.lineEnd()==null) in.setNull(7,Types.INTEGER); else in.setInt(7,i.lineEnd());
                    in.setString(8,i.commitSha()); in.addBatch();
                }
                in.executeBatch();
            }
            connection.createStatement().execute("INSERT INTO knowledge_fts(knowledge_fts) VALUES('rebuild')");
            connection.commit();
        } catch(SQLException e) { connection.rollback(); throw e; }
        finally { connection.setAutoCommit(true); }
    }

    List<String> search(String query, int limit) throws SQLException {
        List<String> rows=new ArrayList<>();
        try(PreparedStatement ps=connection.prepareStatement("""
            SELECT r.name,k.kind,k.name,k.source_path,k.line_start,k.commit_sha,
                   snippet(knowledge_fts,1,'[',']',' … ',24)
            FROM knowledge_fts JOIN knowledge k ON k.id=knowledge_fts.rowid
            JOIN repositories r ON r.id=k.repository_id
            WHERE knowledge_fts MATCH ? ORDER BY bm25(knowledge_fts) LIMIT ?""")) {
            ps.setString(1, query); ps.setInt(2,limit);
            try(ResultSet rs=ps.executeQuery()) {
                while(rs.next()) rows.add("%s | %s | %s | %s:%s | %s%n  %s".formatted(
                    rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                    Objects.toString(rs.getObject(5),"-"),rs.getString(6),rs.getString(7)));
            }
        }
        return rows;
    }

    @Override public void close() throws SQLException { connection.close(); }
}
