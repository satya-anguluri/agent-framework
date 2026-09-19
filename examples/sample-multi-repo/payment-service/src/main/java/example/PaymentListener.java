package example;
import org.springframework.kafka.annotation.KafkaListener;
class PaymentListener {
  @KafkaListener(topics="orders.created")
  void onOrderCreated(String orderId){}
}
