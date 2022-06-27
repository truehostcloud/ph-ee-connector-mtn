package org.mifos.connector.mtn.flowcomponents.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.zeebe.client.ZeebeClient;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.mifos.connector.mtn.zeebe.ZeebeVariables.*;

@Component
public class CollectionResponseProcessor implements Processor {

    private final ZeebeClient zeebeClient;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${zeebe.client.ttl}")
    private int timeToLive;

    public CollectionResponseProcessor(ZeebeClient zeebeClient) {
        this.zeebeClient = zeebeClient;
    }


    @Override
    public void process(Exchange exchange) throws JsonProcessingException {
        Map<String, Object> variables = new HashMap<>();
        String clientCorrelationId = exchange.getProperty(TRANSACTION_ID, String.class);
        logger.info("Received correlation ID  "+ clientCorrelationId );
        variables.put(TRANSACTION_ID, clientCorrelationId);
        variables.put(TRANSACTION_FAILED,  exchange.getProperty(TRANSACTION_FAILED, Boolean.class));

        logger.info("Publishing transaction message variables: " + variables);
        zeebeClient.newPublishMessageCommand()
                .messageName(TRANSFER_MESSAGE)
                .correlationKey(clientCorrelationId)
                .timeToLive(Duration.ofMillis(timeToLive))
                .variables(variables)
                .send()
                .join();
    }
}
