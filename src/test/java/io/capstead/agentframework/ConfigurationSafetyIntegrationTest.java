package io.capstead.agentframework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationSafetyIntegrationTest{
 @TempDir Path root;
 @Test void scannerIndexesConfigurationShapeButNeverRawValues()throws Exception{
  Path config=root.resolve("application.yml");
  Files.writeString(config,"service:\n  endpoint: https://internal.example\n  password: do-not-store\n");
  var items=new RepositoryScanner().scan(root,"a".repeat(40));
  assertTrue(items.stream().anyMatch(i->i.name().equals("service.endpoint")));
  assertFalse(items.toString().contains("internal.example"));
  assertFalse(items.toString().contains("do-not-store"));
 }
 @Test void scannerStillRejectsSecretNamedFiles()throws Exception{
  Files.writeString(root.resolve("secrets.yaml"),"token: do-not-store");
  assertTrue(new RepositoryScanner().scan(root,"a".repeat(40)).isEmpty());
 }
}
