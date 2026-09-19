package io.capstead.agentframework.extract;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class JavaSpringDependencyExtractorTest{
 @Test void extractsDependenciesWithoutProjectSpecificNames(){
  String source="""
   @FeignClient(name="inventory-service") interface Client {}
   @KafkaListener(topics="orders.created") void consume(){}
   void send(){ kafkaTemplate.send("payments.requested",event); }
   @SqsListener("audit-events") void audit(){}
   """;
  var items=new JavaSpringDependencyExtractor().extract(Path.of("src/Client.java"),source,source,"a".repeat(40));
  assertTrue(items.stream().anyMatch(i->i.name().equals("http:inventory-service")));
  assertTrue(items.stream().anyMatch(i->i.name().equals("kafka:orders.created")));
  assertTrue(items.stream().anyMatch(i->i.name().equals("kafka:payments.requested")));
  assertTrue(items.stream().anyMatch(i->i.name().equals("sqs:audit-events")));
 }
}
