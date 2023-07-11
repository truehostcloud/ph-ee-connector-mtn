package org.mifos.connector.mtn.flowcomponents.transaction;

import static org.mifos.connector.mtn.camel.config.CamelProperties.*;
import static org.mifos.connector.mtn.camel.config.CamelProperties.MTN_API_RESPONSE;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.TRANSACTION_FAILED;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.TRANSACTION_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.Map;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.common.channel.dto.TransactionChannelC2BRequestDTO;
import org.mifos.connector.mtn.Utility.MtnUtils;
import org.mifos.connector.mtn.dto.PaymentRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MtnWorker {

    private ZeebeClient zeebeClient;
    private ObjectMapper objectMapper;
    private MtnUtils mtnUtils;
    private CamelContext camelContext;
    private ProducerTemplate producerTemplate;
    private final Logger logger;

    private MtnWorker(ZeebeClient zeebeClient, ObjectMapper objectMapper, MtnUtils mtnUtils, CamelContext camelContext,
            ProducerTemplate producerTemplate) {
        this.zeebeClient = zeebeClient;
        this.objectMapper = objectMapper;
        this.mtnUtils = mtnUtils;
        this.camelContext = camelContext;
        this.producerTemplate = producerTemplate;
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    @PostConstruct
    public void setupWorkers() {

        zeebeClient.newWorker().jobType("init-momo-transfer").handler((client, job) -> {
            logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(),
                    job.getKey());
            Map<String, Object> variables = job.getVariablesAsMap();
            String transactionId = (String) variables.get(TRANSACTION_ID);
            TransactionChannelC2BRequestDTO channelRequest = objectMapper
                    .readValue((String) variables.get("mpesaChannelRequest"), TransactionChannelC2BRequestDTO.class);
            PaymentRequestDTO paymentRequestDTO = mtnUtils.channelRequestConvertor(channelRequest, transactionId);
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setProperty(BUY_GOODS_REQUEST_BODY, paymentRequestDTO);
            exchange.setProperty(CORRELATION_ID, UUID.randomUUID());
            exchange.setProperty(DEPLOYED_PROCESS, job.getBpmnProcessId());
            variables.put(CORRELATION_ID, exchange.getProperty(CORRELATION_ID));
            logger.info("CorrelationID: '{}'!", exchange.getProperty(CORRELATION_ID));
            producerTemplate.send("direct:request-to-pay-base", exchange);
            variables.put(MTN_API_RESPONSE, exchange.getProperty(MTN_API_RESPONSE));
            boolean isTransactionFailed = exchange.getProperty(TRANSACTION_FAILED, boolean.class);
            if (isTransactionFailed) {
                variables.put(TRANSACTION_FAILED, true);
            } else {
                variables.put(TRANSACTION_FAILED, false);
            }
            client.newCompleteCommand(job.getKey()).variables(variables).send().join();
        }).name("init-momo-transfer").maxJobsActive(100).open();
    }
}
