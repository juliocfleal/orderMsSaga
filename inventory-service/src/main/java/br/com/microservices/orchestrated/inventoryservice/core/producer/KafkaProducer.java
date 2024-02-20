package br.com.microservices.orchestrated.inventoryservice.core.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class KafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${spring.kafka.topic.orchestrator}")
    private String orchestrator;

    public void sendEvent(String payload){

        try{
            log.info("Sending event to topic {} with data {}", orchestrator, payload);
            kafkaTemplate.send(orchestrator, payload);
        }catch (Exception exception){
            log.error("Error trying to send data to topic {} with data {}", orchestrator, payload,exception );
        }

    }
}
