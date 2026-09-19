package io.capstead.agentframework.extract;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CiCdVaultExtractorTest{
 private final CiCdVaultExtractor extractor=new CiCdVaultExtractor();
 @Test void extractsJenkinsStructureWithoutShellOrCredentials(){
  String source="""
    pipeline { stages {
      stage('Build') { steps { sh 'curl -H token:super-secret https://private' } }
      stage("Deploy") { steps { build job: 'release-service' } }
    }}
    """;
  var items=extractor.extract(Path.of("Jenkinsfile"),source,source,"a".repeat(40));
  assertTrue(items.stream().anyMatch(i->i.kind().equals("pipeline-stage")&&i.name().equals("Build")));
  assertTrue(items.stream().anyMatch(i->i.kind().equals("pipeline-job")&&i.name().equals("release-service")));
  assertFalse(items.toString().contains("super-secret"));
  assertFalse(items.toString().contains("curl"));
 }
 @Test void extractsActionsAndVaultPolicyReferences(){
  String workflow="steps:\n  - uses: actions/checkout@v4\n  - uses: org/shared-action@v2\n";
  var actions=extractor.extract(Path.of(".github/workflows/ci.yml"),workflow,workflow,"b".repeat(40));
  assertTrue(actions.stream().anyMatch(i->i.name().equals("actions/checkout@v4")));
  String policy="path \"kv/data/service-a/*\" { capabilities = [\"read\"] }";
  var vault=extractor.extract(Path.of("policy.hcl"),policy,policy,"c".repeat(40));
  assertTrue(vault.stream().anyMatch(i->i.kind().equals("vault-path")&&i.name().equals("kv/data/service-a/*")));
 }
}
