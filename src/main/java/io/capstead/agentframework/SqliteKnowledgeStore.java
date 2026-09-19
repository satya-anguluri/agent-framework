package io.capstead.agentframework;

import io.capstead.agentframework.model.KnowledgeItem;
import io.capstead.agentframework.model.JiraWorkItem;
import io.capstead.agentframework.model.HistoryCommit;
import io.capstead.agentframework.model.WorkItem;
import io.capstead.agentframework.model.AnalysisEvidence;
import io.capstead.agentframework.model.AnalysisCategory;
import io.capstead.agentframework.model.ContextRelationship;
import io.capstead.agentframework.extract.AnalysisCategoryRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

final class SqliteKnowledgeStore implements AutoCloseable {
    record RepositoryState(String name, Path root, String indexedCommit) {}
    record QuestionQuery(String fts,List<String> terms) {}
    private static final AnalysisCategoryRegistry CATEGORY_REGISTRY=new AnalysisCategoryRegistry();
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
            rebuildHistoryKnowledgeLinks();
            rebuildDependencyEdges();
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
        WITH seeds AS (SELECT rowid FROM knowledge_fts WHERE knowledge_fts MATCH ? ORDER BY bm25(knowledge_fts) LIMIT ?),
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
            if(countParameters(sql)>2)ps.setInt(3,limit);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add("%s | %s | %s | %s:%s | %s%n  %s".formatted(
                rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),Objects.toString(rs.getObject(5),"-"),
                rs.getString(6),rs.getString(7)));}
        }
        return rows;
    }

    QuestionQuery questionQuery(String question){
        Set<String> stop=Set.of("the","and","for","with","from","that","this","does","how","what","when",
          "where","which","currently","current","work","works","working","into","your","our","are","was");
        List<String> raw=new ArrayList<>();
        var matcher=java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_-]{1,}").matcher(question);
        while(matcher.find())raw.add(matcher.group().toLowerCase(Locale.ROOT).replace("-",""));
        LinkedHashSet<String> terms=new LinkedHashSet<>();
        for(String term:raw)if(term.length()>=3&&!stop.contains(term))terms.add(term);
        for(int i=0;i+1<raw.size();i++){
            String joined=raw.get(i)+raw.get(i+1);
            if(joined.length()>=5&&!stop.contains(raw.get(i))&&!stop.contains(raw.get(i+1)))terms.add(joined);
        }
        List<String> bounded=terms.stream().limit(16).toList();
        String fts=bounded.stream().map(t->"\""+t.replace("\"","")+"\"*").collect(java.util.stream.Collectors.joining(" OR "));
        return new QuestionQuery(fts,bounded);
    }

    List<AnalysisEvidence> contextEvidence(String query,int limit)throws SQLException{
        List<AnalysisEvidence> rows=new ArrayList<>();
        try(PreparedStatement ps=connection.prepareStatement("""
          SELECT r.name,k.kind,k.name,k.source_path,k.line_start,k.commit_sha,
                 substr(replace(k.content,char(10),' '),1,320)
          FROM knowledge_fts f JOIN knowledge k ON k.id=f.rowid
          JOIN repositories r ON r.id=k.repository_id
          WHERE knowledge_fts MATCH ?
          ORDER BY bm25(knowledge_fts),r.name,k.source_path,coalesce(k.line_start,0),k.kind,k.name LIMIT ?""")){
            ps.setString(1,query);ps.setInt(2,limit);
            try(ResultSet rs=ps.executeQuery()){while(rs.next()){
                String kind=rs.getString(2),path=rs.getString(4);Number line=(Number)rs.getObject(5);
                rows.add(new AnalysisEvidence(CATEGORY_REGISTRY.classify(kind,path),rs.getString(1),kind,
                  rs.getString(3),path,line==null?null:line.intValue(),rs.getString(6),rs.getString(7)));
            }}
        }
        return List.copyOf(rows);
    }

    List<ContextRelationship> contextRelationships(String query,int limit)throws SQLException{
        List<ContextRelationship> rows=new ArrayList<>();
        try(PreparedStatement ps=connection.prepareStatement("""
          WITH seeds AS (
            SELECT rowid FROM knowledge_fts WHERE knowledge_fts MATCH ? ORDER BY bm25(knowledge_fts) LIMIT ?
          ), edges AS (
            SELECT e.dependency_type type,e.evidence evidence,
              sr.name source_repository,sk.kind source_kind,sk.name source_name,sk.source_path source_path,
              sk.line_start source_line,sk.commit_sha source_commit,
              coalesce(tr.name,'unresolved') target_repository,coalesce(tk.kind,'repository') target_kind,
              coalesce(tk.name,e.artifact_name) target_name,tk.source_path target_path,
              tk.line_start target_line,tk.commit_sha target_commit
            FROM dependency_edges e
            JOIN knowledge sk ON sk.id=e.source_knowledge_id JOIN repositories sr ON sr.id=e.source_repository_id
            LEFT JOIN knowledge tk ON tk.id=e.target_knowledge_id LEFT JOIN repositories tr ON tr.id=e.target_repository_id
            WHERE e.source_knowledge_id IN (SELECT rowid FROM seeds) OR e.target_knowledge_id IN (SELECT rowid FROM seeds)
            UNION
            SELECT rel.relation,rel.evidence,
              sr.name,sk.kind,sk.name,sk.source_path,sk.line_start,sk.commit_sha,
              tr.name,tk.kind,tk.name,tk.source_path,tk.line_start,tk.commit_sha
            FROM relationships rel JOIN knowledge sk ON sk.id=rel.source_knowledge_id
            JOIN repositories sr ON sr.id=sk.repository_id
            JOIN knowledge tk ON tk.kind=rel.target_kind AND lower(tk.name)=lower(rel.target_name) AND tk.id<>sk.id
            JOIN repositories tr ON tr.id=tk.repository_id
            WHERE rel.source_knowledge_id IN (SELECT rowid FROM seeds)
          )
          SELECT DISTINCT * FROM edges
          ORDER BY type,source_repository,source_path,coalesce(source_line,0),target_repository,target_path LIMIT ?""")){
            ps.setString(1,query);ps.setInt(2,limit);ps.setInt(3,limit);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(new ContextRelationship(
              rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),
              integer(rs,7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),
              integer(rs,13),rs.getString(14)));
            }
        }
        return List.copyOf(rows);
    }

    private Integer integer(ResultSet rs,int column)throws SQLException{
        Number value=(Number)rs.getObject(column);return value==null?null:value.intValue();
    }


    void upsertJiraWork(JiraWorkItem item)throws SQLException{
        connection.setAutoCommit(false);
        try{
            try(PreparedStatement ps=connection.prepareStatement("""
                INSERT INTO jira_work(jira_key,summary,description,acceptance_criteria,updated_at)
                VALUES(?,?,?,?,?) ON CONFLICT(jira_key) DO UPDATE SET summary=excluded.summary,
                description=excluded.description,acceptance_criteria=excluded.acceptance_criteria,
                updated_at=excluded.updated_at""")){
                ps.setString(1,item.key());ps.setString(2,item.summary());ps.setString(3,item.description());
                ps.setString(4,item.acceptanceCriteria());ps.setString(5,Instant.now().toString());ps.executeUpdate();
            }
            try(PreparedStatement ps=connection.prepareStatement("""
                INSERT INTO jira_sources(jira_key,source_url,source_updated_at,imported_at)
                VALUES(?,?,?,?) ON CONFLICT(jira_key) DO UPDATE SET source_url=excluded.source_url,
                source_updated_at=excluded.source_updated_at,imported_at=excluded.imported_at""")){
                ps.setString(1,item.key());ps.setString(2,item.sourceUrl());ps.setString(3,item.sourceUpdatedAt());
                ps.setString(4,Instant.now().toString());ps.executeUpdate();
            }
            connection.commit();
        }catch(SQLException e){connection.rollback();throw e;}finally{connection.setAutoCommit(true);}
    }

    Optional<JiraWorkItem> jiraWork(String key)throws SQLException{
        try(PreparedStatement ps=connection.prepareStatement("""
            SELECT w.jira_key,w.summary,w.description,w.acceptance_criteria,s.source_url,s.source_updated_at
            FROM jira_work w JOIN jira_sources s ON s.jira_key=w.jira_key WHERE w.jira_key=?""")){
            ps.setString(1,key);
            try(ResultSet rs=ps.executeQuery()){
                if(!rs.next())return Optional.empty();
                return Optional.of(new JiraWorkItem(rs.getString(1),rs.getString(2),rs.getString(3),
                        rs.getString(4),rs.getString(5),rs.getString(6)));
            }
        }
    }

    List<String> jiraBlastRadius(String key,int limit)throws SQLException{
        JiraWorkItem item=jiraWork(key).orElseThrow(()->new IllegalArgumentException("Unknown Jira key: "+key));
        String query=ftsQuery(item.summary()+" "+Objects.toString(item.description(),"")+" "+
                Objects.toString(item.acceptanceCriteria(),""));
        if(query.isBlank())return List.of();
        return blastRadius(query,limit);
    }

    private String ftsQuery(String text){
        LinkedHashSet<String> terms=new LinkedHashSet<>();
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_/-]{2,}").matcher(text);
        while(matcher.find()&&terms.size()<12)terms.add("\""+matcher.group().replace("\"","")+"\"");
        return String.join(" OR ",terms);
    }


    void replaceHistory(String repositoryName,List<HistoryCommit> commits)throws SQLException{
        long repoId;
        try(PreparedStatement q=connection.prepareStatement("SELECT id FROM repositories WHERE name=?")){
            q.setString(1,repositoryName);try(ResultSet rs=q.executeQuery()){
                if(!rs.next())throw new IllegalStateException("Index repository before history: "+repositoryName);
                repoId=rs.getLong(1);
            }
        }
        connection.setAutoCommit(false);
        try{
            try(PreparedStatement del=connection.prepareStatement("DELETE FROM history_commits WHERE repository_id=?")){
                del.setLong(1,repoId);del.executeUpdate();
            }
            try(PreparedStatement commit=connection.prepareStatement("""
                    INSERT INTO history_commits(repository_id,commit_sha,committed_at,subject) VALUES(?,?,?,?)""");
                PreparedStatement file=connection.prepareStatement("""
                    INSERT INTO commit_files(repository_id,commit_sha,file_path) VALUES(?,?,?)""");
                PreparedStatement jira=connection.prepareStatement("""
                    INSERT INTO commit_jira(repository_id,commit_sha,jira_key) VALUES(?,?,?)""");
                PreparedStatement lineage=connection.prepareStatement("""
                    INSERT INTO commit_file_lineage(repository_id,commit_sha,original_path,current_path)
                    VALUES(?,?,?,?)""")){
                for(HistoryCommit item:commits){
                    commit.setLong(1,repoId);commit.setString(2,item.sha());commit.setString(3,item.committedAt());
                    commit.setString(4,item.subject());commit.addBatch();
                    for(String path:item.files()){
                        file.setLong(1,repoId);file.setString(2,item.sha());file.setString(3,path);file.addBatch();
                        lineage.setLong(1,repoId);lineage.setString(2,item.sha());lineage.setString(3,path);
                        lineage.setString(4,item.currentPath(path));lineage.addBatch();
                    }
                    for(String key:item.jiraKeys()){jira.setLong(1,repoId);jira.setString(2,item.sha());jira.setString(3,key);jira.addBatch();}
                }
                commit.executeBatch();file.executeBatch();jira.executeBatch();lineage.executeBatch();
            }
            rebuildHistoryKnowledgeLinks();
            connection.commit();
        }catch(SQLException e){connection.rollback();throw e;}finally{connection.setAutoCommit(true);}
    }

    List<String> jiraHistory(String key,int limit)throws SQLException{
        List<String> rows=new ArrayList<>();
        try(PreparedStatement ps=connection.prepareStatement("""
            SELECT r.name,h.commit_sha,h.committed_at,h.subject,
                   coalesce(group_concat(CASE WHEN f.original_path=f.current_path THEN f.original_path
                     ELSE f.original_path||' -> '||f.current_path END,char(10)),'')
            FROM commit_jira j JOIN history_commits h ON h.repository_id=j.repository_id AND h.commit_sha=j.commit_sha
            JOIN repositories r ON r.id=h.repository_id
            LEFT JOIN commit_file_lineage f ON f.repository_id=h.repository_id AND f.commit_sha=h.commit_sha
            WHERE j.jira_key=? GROUP BY r.name,h.commit_sha,h.committed_at,h.subject
            ORDER BY h.committed_at DESC LIMIT ?""")){
            ps.setString(1,key.toUpperCase(Locale.ROOT));ps.setInt(2,limit);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(
                "%s | %s | %s%n%s%nFiles:%n%s".formatted(rs.getString(1),rs.getString(2),rs.getString(3),
                 rs.getString(4),rs.getString(5)));
            }
        }
        return rows;
    }


    private void rebuildHistoryKnowledgeLinks()throws SQLException{
        connection.createStatement().execute("DELETE FROM commit_knowledge");
        connection.createStatement().execute("""
            INSERT OR IGNORE INTO commit_knowledge(repository_id,commit_sha,knowledge_id)
            SELECT f.repository_id,f.commit_sha,k.id
            FROM commit_file_lineage f JOIN knowledge k
              ON k.repository_id=f.repository_id AND k.source_path=f.current_path""");
    }

    List<String> relatedJiras(String query,int limit)throws SQLException{
        List<String> rows=new ArrayList<>();
        try(PreparedStatement ps=connection.prepareStatement("""
            WITH seeds AS (
              SELECT k.id,k.kind,k.name,k.source_path,k.repository_id
              FROM knowledge_fts f JOIN knowledge k ON k.id=f.rowid
              WHERE knowledge_fts MATCH ? ORDER BY bm25(knowledge_fts) LIMIT ?
            )
            SELECT DISTINCT j.jira_key,r.name,h.commit_sha,h.committed_at,h.subject,
                   s.kind,s.name,s.source_path
            FROM seeds s JOIN commit_knowledge ck ON ck.knowledge_id=s.id
            JOIN commit_jira j ON j.repository_id=ck.repository_id AND j.commit_sha=ck.commit_sha
            JOIN history_commits h ON h.repository_id=ck.repository_id AND h.commit_sha=ck.commit_sha
            JOIN repositories r ON r.id=ck.repository_id
            ORDER BY h.committed_at DESC LIMIT ?""")){
            ps.setString(1,query);ps.setInt(2,limit);ps.setInt(3,limit);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(
                "%s | %s | %s | %s%n%s%nCurrent evidence: %s %s at %s".formatted(
                 rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                 rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8)));
            }
        }
        return rows;
    }


    private void rebuildDependencyEdges()throws SQLException{
        connection.createStatement().execute("DELETE FROM dependency_edges");
        connection.createStatement().execute("""
            INSERT OR IGNORE INTO dependency_edges(source_repository_id,source_knowledge_id,target_repository_id,
              target_knowledge_id,dependency_type,artifact_name,evidence)
            SELECT p.repository_id,p.id,c.repository_id,c.id,'message',p.name,
                   'matching producer and consumer destination'
            FROM knowledge p JOIN knowledge c ON c.name=p.name
            WHERE p.kind='message-producer' AND c.kind='message-consumer' AND p.repository_id<>c.repository_id""");
        connection.createStatement().execute("""
            INSERT OR IGNORE INTO dependency_edges(source_repository_id,source_knowledge_id,target_repository_id,
              target_knowledge_id,dependency_type,artifact_name,evidence)
            SELECT c.repository_id,c.id,r.id,NULL,'service',c.name,
                   'client name matches configured repository'
            FROM knowledge c JOIN repositories r ON lower(r.name)=lower(substr(c.name,instr(c.name,':')+1))
            WHERE c.kind='service-client' AND c.repository_id<>r.id""");
    }
    List<String> dependencyGraph()throws SQLException{
        List<String> rows=new ArrayList<>();
        try(ResultSet rs=connection.createStatement().executeQuery("""
            SELECT source.name,sk.kind,sk.name,coalesce(target.name,'unresolved'),
              coalesce(tk.kind,'repository'),coalesce(tk.name,e.artifact_name),e.dependency_type,
              e.evidence,sk.source_path,sk.commit_sha
            FROM dependency_edges e JOIN repositories source ON source.id=e.source_repository_id
            JOIN knowledge sk ON sk.id=e.source_knowledge_id
            LEFT JOIN repositories target ON target.id=e.target_repository_id
            LEFT JOIN knowledge tk ON tk.id=e.target_knowledge_id
            ORDER BY source.name,e.dependency_type,e.artifact_name""")){
            while(rs.next())rows.add("%s [%s %s] -> %s [%s %s] via %s%n  Evidence: %s at %s @ %s".formatted(
              rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),
              rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10)));
        }return rows;
    }

    void upsertWorkItem(WorkItem item)throws SQLException{
        connection.setAutoCommit(false);
        try{
            try(PreparedStatement ps=connection.prepareStatement("""
              INSERT INTO work_items(work_item_key,source_system,summary,description,acceptance_criteria,status,updated_at)
              VALUES(?,?,?,?,?,?,?) ON CONFLICT(source_system,work_item_key) DO UPDATE SET
              summary=excluded.summary,description=excluded.description,acceptance_criteria=excluded.acceptance_criteria,
              status=excluded.status,updated_at=excluded.updated_at""")){
                ps.setString(1,item.key());ps.setString(2,item.sourceSystem());ps.setString(3,item.summary());
                ps.setString(4,item.description());ps.setString(5,item.acceptanceCriteria());ps.setString(6,item.status());
                ps.setString(7,Instant.now().toString());ps.executeUpdate();
            }
            try(PreparedStatement ps=connection.prepareStatement("""
              INSERT INTO work_item_sources(source_system,work_item_key,source_url,source_updated_at,imported_at)
              VALUES(?,?,?,?,?) ON CONFLICT(source_system,work_item_key) DO UPDATE SET
              source_url=excluded.source_url,source_updated_at=excluded.source_updated_at,
              imported_at=excluded.imported_at""")){
                ps.setString(1,item.sourceSystem());ps.setString(2,item.key());ps.setString(3,item.sourceUrl());
                ps.setString(4,item.sourceUpdatedAt());ps.setString(5,Instant.now().toString());ps.executeUpdate();
            }connection.commit();
        }catch(SQLException e){connection.rollback();throw e;}finally{connection.setAutoCommit(true);}
    }
    Optional<WorkItem> workItem(String key)throws SQLException{
        try(PreparedStatement ps=connection.prepareStatement("""
          SELECT w.work_item_key,w.source_system,w.summary,w.description,w.acceptance_criteria,w.status,
                 s.source_url,s.source_updated_at
          FROM work_items w JOIN work_item_sources s
            ON s.source_system=w.source_system AND s.work_item_key=w.work_item_key
          WHERE (w.source_system || ':' || w.work_item_key)=? OR w.work_item_key=?
          ORDER BY w.source_system""")){
            ps.setString(1,key);ps.setString(2,key);try(ResultSet rs=ps.executeQuery()){
              if(rs.next()){
                WorkItem found=new WorkItem(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                  rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8));
                if(rs.next())throw new IllegalArgumentException(
                  "Ambiguous work-item key '"+key+"'; use <source-system>:"+key);
                return Optional.of(found);
              }
            }
        }
        return jiraWork(key).map(j->new WorkItem(j.key(),"jira",j.summary(),j.description(),
          j.acceptanceCriteria(),null,j.sourceUrl(),j.sourceUpdatedAt()));
    }
    List<AnalysisEvidence> workItemEvidenceDetails(String key,int limit)throws SQLException{
        if(limit<1)throw new IllegalArgumentException("limit must be positive");
        WorkItem item=requiredWorkItem(key);String query=ftsQuery(item.searchableText());
        if(query.isBlank())return List.of();
        List<AnalysisEvidence> rows=new ArrayList<>();
        try(PreparedStatement ps=connection.prepareStatement("""
          WITH seeds AS (
            SELECT rowid FROM knowledge_fts WHERE knowledge_fts MATCH ?
            ORDER BY bm25(knowledge_fts) LIMIT ?
          ), expanded AS (
            SELECT k.id FROM knowledge k JOIN seeds s ON s.rowid=k.id
            UNION
            SELECT target.id FROM seeds s JOIN relationships rel ON rel.source_knowledge_id=s.rowid
            JOIN knowledge source ON source.id=s.rowid
            JOIN knowledge target ON target.kind=rel.target_kind AND lower(target.name)=lower(rel.target_name)
            WHERE target.id<>source.id
          )
          SELECT DISTINCT r.name,k.kind,k.name,k.source_path,k.line_start,k.commit_sha,
            substr(replace(k.content,char(10),' '),1,240)
          FROM expanded e JOIN knowledge k ON k.id=e.id JOIN repositories r ON r.id=k.repository_id
          ORDER BY r.name,k.source_path,k.line_start,k.kind,k.name LIMIT ?""")){
            ps.setString(1,query);ps.setInt(2,limit);ps.setInt(3,limit);
            try(ResultSet rs=ps.executeQuery()){while(rs.next()){
                String kind=rs.getString(2),path=rs.getString(4);
                Number line=(Number)rs.getObject(5);
                rows.add(new AnalysisEvidence(CATEGORY_REGISTRY.classify(kind,path),rs.getString(1),kind,
                  rs.getString(3),path,line==null?null:line.intValue(),rs.getString(6),rs.getString(7)));
            }}
        }
        return List.copyOf(rows);
    }

    List<String> workItemEvidence(String key,int limit)throws SQLException{
        if(limit<1)throw new IllegalArgumentException("limit must be positive");
        WorkItem item=requiredWorkItem(key);String query=ftsQuery(item.searchableText());
        return query.isBlank()?List.of():blastRadius(query,limit);
    }
    List<String> relatedWorkItems(String key,int limit)throws SQLException{
        if(limit<1)throw new IllegalArgumentException("limit must be positive");
        WorkItem item=requiredWorkItem(key);String query=ftsQuery(item.searchableText());
        return query.isBlank()?List.of():relatedJiras(query,limit);
    }
    List<String> workItemDependencies(String key,int limit)throws SQLException{
        if(limit<1)throw new IllegalArgumentException("limit must be positive");
        WorkItem item=requiredWorkItem(key);String query=ftsQuery(item.searchableText());
        if(query.isBlank())return List.of();List<String> rows=new ArrayList<>();
        try(PreparedStatement ps=connection.prepareStatement("""
          WITH seeds AS (SELECT rowid FROM knowledge_fts WHERE knowledge_fts MATCH ? ORDER BY bm25(knowledge_fts) LIMIT ?)
          SELECT DISTINCT source.name,sk.kind,sk.name,coalesce(target.name,'unresolved'),
            coalesce(tk.kind,'repository'),coalesce(tk.name,e.artifact_name),e.dependency_type,
            e.evidence,sk.source_path,sk.commit_sha,
            coalesce(tk.source_path,'unresolved'),coalesce(tk.commit_sha,'unresolved')
          FROM seeds s JOIN dependency_edges e ON e.source_knowledge_id=s.rowid OR e.target_knowledge_id=s.rowid
          JOIN repositories source ON source.id=e.source_repository_id
          JOIN knowledge sk ON sk.id=e.source_knowledge_id
          LEFT JOIN repositories target ON target.id=e.target_repository_id
          LEFT JOIN knowledge tk ON tk.id=e.target_knowledge_id
          ORDER BY source.name,e.dependency_type,e.artifact_name LIMIT ?""")){
            ps.setString(1,query);ps.setInt(2,limit);ps.setInt(3,limit);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(
              "%s [%s %s] -> %s [%s %s] via %s%n  Evidence: %s%n  Source: %s @ %s%n  Target: %s @ %s".formatted(
               rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),
               rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),
               rs.getString(11),rs.getString(12)));
            }
        }return rows;
    }
    private WorkItem requiredWorkItem(String key)throws SQLException{
        return workItem(key).orElseThrow(()->new IllegalArgumentException("Unknown work item: "+key));
    }
    private int countParameters(String sql){return (int)sql.chars().filter(c->c=='?').count();}
    @Override public void close()throws SQLException{connection.close();}
}
