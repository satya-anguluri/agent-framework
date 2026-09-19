package io.capstead.agentframework.model;

public enum AnalysisCategory {
    CODE,
    API_AND_MESSAGES,
    DATA,
    HELM_AND_KUBERNETES,
    APPLICATION_CONFIGURATION,
    VAULT,
    CI_CD,
    TESTS,
    OTHER;

    public static AnalysisCategory classify(String kind,String path){
        String k=kind==null?"":kind.toLowerCase();
        String p=path==null?"":path.toLowerCase();
        if(k.contains("test")||p.contains("/test/")||p.endsWith("test.java"))return TESTS;
        if(k.startsWith("http-")||k.startsWith("message-")||k.equals("service-client"))return API_AND_MESSAGES;
        if(k.startsWith("db-")||k.contains("migration")||p.contains("flyway")||p.contains("liquibase"))return DATA;
        if(k.startsWith("helm-")||k.startsWith("kubernetes-")||k.startsWith("k8s-")||p.contains("/templates/"))return HELM_AND_KUBERNETES;
        if(k.startsWith("config-")||p.contains("application.yml")||p.contains("application.yaml")||p.contains("application.properties"))return APPLICATION_CONFIGURATION;
        if(k.startsWith("vault-")||p.endsWith(".hcl"))return VAULT;
        if(k.startsWith("pipeline-")||k.startsWith("jenkins-")||k.startsWith("github-action")||
           k.startsWith("gitlab-")||k.startsWith("azure-pipeline")||p.endsWith("jenkinsfile"))return CI_CD;
        if(k.startsWith("java-")||k.equals("type")||k.equals("method"))return CODE;
        return OTHER;
    }
}
