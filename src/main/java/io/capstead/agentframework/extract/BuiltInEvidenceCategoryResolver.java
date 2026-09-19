package io.capstead.agentframework.extract;

import io.capstead.agentframework.model.AnalysisCategory;
import java.util.*;

final class BuiltInEvidenceCategoryResolver implements EvidenceCategoryResolver {
    public Optional<AnalysisCategory> classify(String kind,String sourcePath){
        String k=kind==null?"":kind.toLowerCase(Locale.ROOT);
        String p=sourcePath==null?"":sourcePath.toLowerCase(Locale.ROOT);
        String file=p.substring(p.lastIndexOf('/')+1);
        if(k.contains("test")||p.contains("/test/")||file.endsWith("test.java"))return category(AnalysisCategory.TESTS);
        if(k.startsWith("http-")||k.startsWith("message-")||k.equals("service-client"))return category(AnalysisCategory.API_AND_MESSAGES);
        if(k.startsWith("db-")||k.contains("migration")||p.contains("flyway")||p.contains("liquibase"))return category(AnalysisCategory.DATA);
        if(k.startsWith("helm-")||k.startsWith("kubernetes-")||k.startsWith("k8s-")||
           p.contains("/templates/")||file.equals("chart.yaml")||file.equals("chart.lock")||
           file.equals("values.yaml")||(file.startsWith("values-")&&file.endsWith(".yaml")))
            return category(AnalysisCategory.HELM_AND_KUBERNETES);
        if(k.startsWith("config-")||file.equals("application.yml")||file.equals("application.yaml")||
           file.equals("application.properties"))return category(AnalysisCategory.APPLICATION_CONFIGURATION);
        if(k.startsWith("vault-")||file.endsWith(".hcl"))return category(AnalysisCategory.VAULT);
        if(k.startsWith("pipeline-")||k.startsWith("jenkins-")||k.startsWith("github-action")||
           k.startsWith("gitlab-")||k.startsWith("azure-pipeline")||file.equals("jenkinsfile"))
            return category(AnalysisCategory.CI_CD);
        if(k.startsWith("java-")||k.equals("type")||k.equals("method"))return category(AnalysisCategory.CODE);
        return Optional.empty();
    }
    private static Optional<AnalysisCategory> category(AnalysisCategory value){return Optional.of(value);}
}
