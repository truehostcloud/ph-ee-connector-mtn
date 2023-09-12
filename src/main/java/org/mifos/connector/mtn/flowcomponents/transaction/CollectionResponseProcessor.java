package org.mifos.connector.mtn.flowcomponents.transaction;

import static org.mifos.connector.mtn.camel.config.CamelProperties.*;
import static org.mifos.connector.mtn.utility.ZeebeUtils.getNextTimer;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.EXTERNAL_ID;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.FINANCIAL_TRANSACTION_ID;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.GET_TRANSACTION_STATUS_RESPONSE;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.GET_TRANSACTION_STATUS_RESPONSE_CODE;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.SERVER_TRANSACTION_STATUS_RETRY_COUNT;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.TIMER;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.TRANSACTION_FAILED;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.TRANSACTION_ID;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.TRANSFER_MESSAGE;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.ZEEBE_ELEMENT_INSTANCE_KEY;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Process the collection response.
 */
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
        Object updatedRetryCount = exchange.getProperty(SERVER_TRANSACTION_STATUS_RETRY_COUNT);
        if (updatedRetryCount != null) {
            variables.put(SERVER_TRANSACTION_STATUS_RETRY_COUNT, updatedRetryCount);
            Boolean isRetryExceeded = (Boolean) exchange.getProperty(IS_RETRY_EXCEEDED);
            if (isRetryExceeded == null || !isRetryExceeded) {
                String body = exchange.getProperty(LAST_RESPONSE_BODY, String.class);
                Object statusCode = exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE);
                if (body == null) {
                    body = exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_TEXT, String.class);
                }
                if (statusCode == null) {
                    Exception e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    if (null != e && e instanceof HttpOperationFailedException) {
                        HttpOperationFailedException httpOperationFailedException = (HttpOperationFailedException) e;
                        statusCode = httpOperationFailedException.getStatusCode();
                    }
                }
                variables.put(GET_TRANSACTION_STATUS_RESPONSE, body);
                variables.put(GET_TRANSACTION_STATUS_RESPONSE_CODE,
                        exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
            }
        }

        // TODO update pending transaction depending on the response we get from mtn
        Object isTransactionPending = exchange.getProperty(IS_TRANSACTION_PENDING);
        Boolean isRetryExceeded = (Boolean) exchange.getProperty(IS_RETRY_EXCEEDED);

        if (isTransactionPending != null && (boolean) isTransactionPending
                && (isRetryExceeded == null || !isRetryExceeded)) {
            String newTimer = getNextTimer(exchange.getProperty(TIMER, String.class));
            logger.info("Updating retry count to " + updatedRetryCount);
            logger.info("Updating timer value to " + newTimer);
            variables.put(TIMER, newTimer);
            Long elementInstanceKey = (Long) exchange.getProperty(ZEEBE_ELEMENT_INSTANCE_KEY);
            zeebeClient.newSetVariablesCommand(elementInstanceKey).variables(variables).send().join();
            return;
        }
        String transactionId = exchange.getProperty(TRANSACTION_ID, String.class);
        logger.info("Received transaction ID  " + transactionId);
        if (transactionId != null) {
            variables.put(TRANSACTION_ID, transactionId);
        }
        String correlationId = exchange.getProperty(CORRELATION_ID, String.class);
        String financialTransactionId = exchange.getProperty(FINANCIAL_TRANSACTION_ID, String.class);
        logger.info("Received CORRELATION ID  " + correlationId);
        variables.put(CORRELATION_ID, correlationId);
        variables.put(EXTERNAL_ID, financialTransactionId);
        variables.put(TRANSACTION_FAILED, exchange.getProperty(TRANSACTION_FAILED, Boolean.class));
        logger.info("Received financial transaction ID  " + variables.get(FINANCIAL_TRANSACTION_ID));

        if (exchange.getProperty(TRANSACTION_FAILED, Boolean.class)) {
            variables.put(ERROR_INFORMATION, exchange.getProperty(ERROR_INFORMATION, String.class));
            variables.put(ERROR_CODE, exchange.getProperty(ERROR_INFORMATION, String.class));
            variables.put(ERROR_DESCRIPTION, exchange.getProperty(ERROR_DESCRIPTION, String.class));
        }

        logger.info("Publishing transaction message variables: " + variables);
        zeebeClient.newPublishMessageCommand().messageName(TRANSFER_MESSAGE).correlationKey(correlationId)
                .timeToLive(Duration.ofMillis(timeToLive)).variables(variables).send().join();
    }
}
