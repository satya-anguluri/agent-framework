package example;
import org.springframework.stereotype.Service;
@Service
class OrderPublisher {
  void publish(String orderId){
    kafkaTemplate.send("orders.created",orderId);
  }
  private final Object kafkaTemplate=null;
}
