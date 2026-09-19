package io.capstead.agentframework.extract;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class HelmConfigurationExtractorTest{
 private final HelmConfigurationExtractor extractor=new HelmConfigurationExtractor();

 @Test void extractsApplicationStructureWithoutValues(){
  String source="""
    spring:
      datasource:
        url: jdbc:postgresql://private-host/prod
        password: super-secret-value
    downstream:
      url: ${INVENTORY_URL:https://private-default}
    """;
  var items=extractor.extract(Path.of("src/main/resources/application-prod.yml"),source,source,"a".repeat(40));
  assertTrue(items.stream().anyMatch(i->i.kind().equals("config-key")&&i.name().equals("spring.datasource.url")));
  assertTrue(items.stream().anyMatch(i->i.kind().equals("config-reference")&&i.name().equals("INVENTORY_URL")));
  String evidence=items.toString();
  assertFalse(evidence.contains("super-secret-value"));
  assertFalse(evidence.contains("private-host"));
  assertFalse(evidence.contains("private-default"));
 }

 @Test void extractsHelmTemplatesAndValuesReferences(){
  String source="""
    apiVersion: apps/v1
    kind: Deployment
    metadata:
      name: {{ include "service.fullname" . }}
    spec:
      replicas: {{ .Values.replicaCount }}
      template:
        spec:
          containers:
            - image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
    """;
  var items=extractor.extract(Path.of("charts/service/templates/deployment.yaml"),source,source,"b".repeat(40));
  assertTrue(items.stream().anyMatch(i->i.kind().equals("k8s-resource")&&i.name().startsWith("Deployment:")));
  assertTrue(items.stream().anyMatch(i->i.kind().equals("helm-value-reference")&&i.name().equals("replicaCount")));
  assertTrue(items.stream().anyMatch(i->i.name().equals("image.repository")));
 }
}
