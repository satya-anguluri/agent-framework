package io.capstead.agentframework.workitem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class WorkItemAdaptersTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final WorkItemAdapters adapters=new WorkItemAdapters();

    @Test void normalizesGithubIssue()throws Exception{
        var payload=mapper.readTree("""
          {"number":42,"title":"Protect payment retries","body":"Require idempotency",
           "state":"open","updated_at":"2026-09-19T01:00:00Z"}
          """);
        var item=adapters.require("github").read(payload,
          URI.create("https://api.github.com/repos/acme/payments/issues/42"));
        assertEquals("acme.payments:42",item.key());
        assertEquals("github",item.sourceSystem());
        assertEquals("Require idempotency",item.description());
    }

    @Test void normalizesJiraIssue()throws Exception{
        var payload=mapper.readTree("""
          {"key":"PAY-9","fields":{"summary":"Protect payment retries","description":"Require idempotency",
           "status":{"name":"Open"},"updated":"2026-09-19T01:00:00Z"}}
          """);
        var item=adapters.require("jira").read(payload,
          URI.create("https://jira.example.com/rest/api/3/issue/PAY-9"));
        assertEquals("PAY-9",item.key());
        assertEquals("Open",item.status());
    }

    @Test void normalizesLinearGraphqlEnvelope()throws Exception{
        var payload=mapper.readTree("""
          {"data":{"issue":{"identifier":"PAY-10","title":"Trace rollout","description":"Add evidence",
           "state":{"name":"Todo"},"url":"https://linear.app/acme/issue/PAY-10",
           "updatedAt":"2026-09-19T01:00:00Z"}}}
          """);
        var item=adapters.require("linear").read(payload,URI.create("https://api.linear.app/graphql"));
        assertEquals("PAY-10",item.key());
        assertEquals("linear",item.sourceSystem());
    }

    @Test void rejectsUnknownAdapter(){
        var error=assertThrows(IllegalArgumentException.class,()->adapters.require("unknown"));
        assertTrue(error.getMessage().contains("github"));
    }
}
