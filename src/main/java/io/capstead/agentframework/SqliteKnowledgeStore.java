package io.capstead.agentframework;

import io.capstead.agentframework.model.KnowledgeItem;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

final class SqliteKnowledgeStore implements AutoCloseable {
    record RepositoryState(String name, Path root, String indexedCommit) {}
    private final Connection connection;

    SqliteKnowledgeStore(Path db) throws SQLException, IOException {
        Path parent=db.toAbsolutePath().getParent(); if(parent!=null)Files.createDirectories(parent);
        connection=DriverManager.getConnection("jdbc:sqlite:"+db.toAbsolutePath());
        connection.createStatement().execute("PRAGMA foreign_keys=ON");
    }

    void initialize() throws SQLException,IOException {
        try(InputStream in=Objects.requireNonNull(getClass().getResourceAsStream("/schema.sql"))){
            String sql=new String(in.readAllBytes(),StandardCharsets.UTF_8);
            for(String statement:sql.split(";\\s*(?:\\R|$)")) if(!statement.isBlank()) connection.createStatement().execute(statement);
        }
    }

    void replaceRepository(String name,Path root,String branch,String commit,List<KnowledgeItem> items)throws SQLException{
        connection.setAutoCommit(false);
        try{
            long repoId;
            try(PreparedStatement up=connection.prepareStatement("""
                INSERT INTO repositories(name,root_path,default_branch,indexed_commit,indexed_at)
                VALUES(?,?,?,?,?) ON CONFLICT(name) DO UPDATE SET root_path=excluded.root_path,
                default_branch=excluded.default_branch,indexed_commit=excluded.indexed_commit,indexed_at=excluded.indexed_at""")){
                up.setString(1,name);up.setString(2,root.toAbsolutePath().toString());up.setString(3,branch);
                up.setString(4,commit);up.setString(5,Instant.now().toString());up.executeUpdate();
            }
            try(PreparedStatement q=connection.prepareStatement("SELECT id FROM repositories WHERE name=?")){
                q.setString(1,name);try(ResultSet rs=q.executeQuery()){rs.next();repoId=rs.getLong(1);}
            }
            try(PreparedStatement del=connection.prepareStatement("DELETE FROM knowledge WHERE repository_id=?")){
                del.setLong(1,repoId);del.executeUpdate();
            }
            try(PreparedStatement in=connection.prepareStatement("""
                INSERT INTO knowledge(repository_id,kind,name,content,source_path,line_start,line_end,commit_sha)
                VALUES(?,?,?,?,?,?,?,?)""")){
                for(KnowledgeItem i:items){in.setLong(1,repoId);in.setString(2,i.kind());in.setString(3,i.name());
                    in.setString(4,i.content());in.setString(5,i.sourcePath());
                    if(i.lineStart()==null)in.setNull(6,Types.INTEGER);else in.setInt(6,i.lineStart());
                    if(i.lineEnd()==null)in.setNull(7,Types.INTEGER);else in.setInt(7,i.lineEnd());
                    in.setString(8,i.commitSha());in.addBatch();}
                in.executeBatch();
            }
            rebuildRelationships();
            connection.createStatement().execute("INSERT INTO knowledge_fts(knowledge_fts) VALUES('rebuild')");
            connection.commit();
        }catch(SQLException e){connection.rollback();throw e;}finally{connection.setAutoCommit(true);}
    }

    private void rebuildRelationships()throws SQLException{
        connection.createStatement().execute("DELETE FROM relationships");
        connection.createStatement().execute("""
            INSERT OR IGNORE INTO relationships(source_knowledge_id,relation,target_kind,target_name,evidence)
            SELECT a.id,'shared-object',b.kind,b.name,'same extracted name in repository '||rb.name
            FROM knowledge a JOIN repositories ra ON ra.id=a.repository_id
            JOIN knowledge b ON b.kind=a.kind AND lower(b.name)=lower(a.name) AND b.repository_id<>a.repository_id
            JOIN repositories rb ON rb.id=b.repository_id
            WHERE a.kind IN('db-table','http-route')""");
    }

    List<RepositoryState> repositoryStates()throws SQLException{
        List<RepositoryState> rows=new ArrayList<>();
        try(ResultSet rs=connection.createStatement().executeQuery("SELECT name,root_path,indexed_commit FROM repositories ORDER BY name")){
            while(rs.next())rows.add(new RepositoryState(rs.getString(1),Path.of(rs.getString(2)),rs.getString(3)));
        }
        return rows;
    }

    List<String> search(String query,int limit)throws SQLException{return queryRows("""
        SELECT r.name,k.kind,k.name,k.source_path,k.line_start,k.commit_sha,
        snippet(knowledge_fts,1,'[',']',' … ',24)
        FROM knowledge_fts JOIN knowledge k ON k.id=knowledge_fts.rowid JOIN repositories r ON r.id=k.repository_id
        WHERE knowledge_fts MATCH ? ORDER BY bm25(knowledge_fts) LIMIT ?""",query,limit);}

    List<String> blastRadius(String query,int limit)throws SQLException{return queryRows("""
        WITH seeds AS (SELECT rowid FROM knowledge_fts WHERE knowledge_fts MATCH ? LIMIT ?),
        expanded AS (
          SELECT k.id FROM knowledge k JOIN seeds s ON s.rowid=k.id
          UNION
          SELECT target.id FROM seeds s JOIN relationships rel ON rel.source_knowledge_id=s.rowid
          JOIN knowledge source ON source.id=s.rowid
          JOIN knowledge target ON target.kind=rel.target_kind AND lower(target.name)=lower(rel.target_name)
          WHERE target.id<>source.id
        )
        SELECT r.name,k.kind,k.name,k.source_path,k.line_start,k.commit_sha,
               substr(replace(k.content,char(10),' '),1,240)
        FROM expanded e JOIN knowledge k ON k.id=e.id JOIN repositories r ON r.id=k.repository_id
        ORDER BY k.kind,k.name,r.name LIMIT ?""",query,limit);}

    private List<String> queryRows(String sql,String query,int limit)throws SQLException{
        List<String> rows=new ArrayList<>();
        try(PreparedStatement ps=connection.prepareStatement(sql)){
            ps.setString(1,query);ps.setInt(2,limit);
            if(sql.contains("LIMIT ?"""")==false && countParameters(sql)>2)ps.setInt(3,limit);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add("%s | %s | %s | %s:%s | %s%n  %s".formatted(
                rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),Objects.toString(rs.getObject(5),"-"),
                rs.getString(6),rs.getString(7)));}
        }
        return rows;
    }

    private int countParameters(String sql){return (int)sql.chars().filter(c->c=='?').count();}
    @Override public void close()throws SQLException{connection.close();}
}
