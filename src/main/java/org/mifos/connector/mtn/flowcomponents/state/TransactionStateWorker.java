package org.mifos.connector.mtn.flowcomponents.state;

import static org.mifos.connector.mtn.camel.config.CamelProperties.BUY_GOODS_REQUEST_BODY;
import static org.mifos.connector.mtn.camel.config.CamelProperties.CORRELATION_ID;
import static org.mifos.connector.mtn.camel.config.CamelProperties.DEPLOYED_PROCESS;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.SERVER_TRANSACTION_STATUS_RETRY_COUNT;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.TIMER;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.TRANSACTION_ID;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.ZEEBE_ELEMENT_INSTANCE_KEY;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.common.channel.dto.TransactionChannelC2BRequestDTO;
import org.mifos.connector.mtn.dto.PaymentRequestDto;
import org.mifos.connector.mtn.utility.MtnUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Checks on the transaction status.
 */
@Component
public class TransactionStateWorker {

    private final Logger logger;
    @Autowired
    private ZeebeClient zeebeClient;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MtnUtils mtnUtils;
    @Autowired
    private CamelContext camelContext;
    @Autowired
    private ProducerTemplate producerTemplate;
    @Value("${zeebe.client.evenly-allocated-max-jobs}")
    private int workerMaxJobs;

    public TransactionStateWorker(ZeebeClient zeebeClient, ObjectMapper objectMapper, MtnUtils mtnUtils,
            CamelContext camelContext, ProducerTemplate producerTemplate) {
        this.zeebeClient = zeebeClient;
        this.objectMapper = objectMapper;
        this.mtnUtils = mtnUtils;
        this.camelContext = camelContext;
        this.producerTemplate = producerTemplate;
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    /**
     * Sets up Zeebe workers.
     */

    @PostConstruct
    public void setupWorkers() {

        zeebeClient.newWorker().jobType("get-momo-transaction-status").handler((client, job) -> {
            logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(),
                    job.getKey());
            Map<String, Object> variables = job.getVariablesAsMap();
            Integer retryCount = 1 + (Integer) variables.getOrDefault(SERVER_TRANSACTION_STATUS_RETRY_COUNT, 0);
            variables.put(SERVER_TRANSACTION_STATUS_RETRY_COUNT, retryCount);
            logger.info("Trying count: " + retryCount);
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setProperty(CORRELATION_ID, variables.get("correlationId"));
            exchange.setProperty(TRANSACTION_ID, variables.get("transactionId"));
            logger.info("correlation Id: " + variables.get("correlationId"));
            logger.info("transactionId : " + variables.get("transactionId"));
            TransactionChannelC2BRequestDTO channelRequest = objectMapper
                    .readValue((String) variables.get("mpesaChannelRequest"), TransactionChannelC2BRequestDTO.class);
            PaymentRequestDto paymentRequestDto = mtnUtils.channelRequestConvertor(channelRequest,
                    variables.get("transactionId").toString());
            // TODO:SAVE SERVER ID
            exchange.setProperty(BUY_GOODS_REQUEST_BODY, paymentRequestDto);
            exchange.setProperty(SERVER_TRANSACTION_STATUS_RETRY_COUNT, retryCount);
            exchange.setProperty(ZEEBE_ELEMENT_INSTANCE_KEY, job.getElementInstanceKey());
            exchange.setProperty(TIMER, variables.get(TIMER));
            exchange.setProperty(DEPLOYED_PROCESS, job.getBpmnProcessId());

            producerTemplate.send("direct:mtn-get-transaction-status-base", exchange);
            client.newCompleteCommand(job.getKey()).send().join();

        }).name("get-momo-transaction-status").maxJobsActive(workerMaxJobs).open();
    }
}
