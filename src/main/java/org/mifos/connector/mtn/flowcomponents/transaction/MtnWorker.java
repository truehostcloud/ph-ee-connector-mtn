package org.mifos.connector.mtn.flowcomponents.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.common.channel.dto.TransactionChannelC2BRequestDTO;
import org.mifos.connector.mtn.Utility.MtnUtils;
import org.mifos.connector.mtn.dto.PaymentRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.Map;
import static org.mifos.connector.mtn.camel.config.CamelProperties.*;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.TRANSACTION_FAILED;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.TRANSACTION_ID;
@Component
public class MtnWorker {
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
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @PostConstruct
    public void setupWorkers() {

        zeebeClient.newWorker()
                .jobType("init-transfer")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                    Map<String, Object> variables = job.getVariablesAsMap();
                    String transactionId = (String) variables.get(TRANSACTION_ID);
                    TransactionChannelC2BRequestDTO channelRequest = objectMapper.readValue(
                            (String) variables.get("mpesaChannelRequest"), TransactionChannelC2BRequestDTO.class);
                    PaymentRequestDTO paymentRequestDTO = mtnUtils.channelRequestConvertor(channelRequest, transactionId);
                    Exchange exchange = new DefaultExchange(camelContext);
                    exchange.setProperty(BUY_GOODS_REQUEST_BODY, paymentRequestDTO);
                    exchange.setProperty(CORRELATION_ID, transactionId);
                    exchange.setProperty(DEPLOYED_PROCESS, job.getBpmnProcessId());
                    producerTemplate.send("direct:request-to-pay-base", exchange);
                    boolean isTransactionFailed = exchange.getProperty(TRANSACTION_FAILED, boolean.class);
                    if (isTransactionFailed) {
                        variables.put(TRANSACTION_FAILED, true);
                    } else {
                        variables.put(TRANSACTION_FAILED, false);
                    }
                    client.newCompleteCommand(job.getKey())
                            .variables(variables)
                            .send()
                            .join();
                })
                .name("init-transfer")
                .maxJobsActive(100)
                .open();
    }
}