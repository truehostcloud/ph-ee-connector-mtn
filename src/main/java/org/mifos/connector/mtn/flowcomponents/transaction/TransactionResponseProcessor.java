package org.mifos.connector.mtn.flowcomponents.transaction;

import static org.mifos.connector.mtn.zeebe.ZeebeVariables.TRANSACTION_FAILED;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

@Component
public class TransactionResponseProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {

        Object hasTransferFailed = exchange.getProperty(TRANSACTION_FAILED);

        if (hasTransferFailed != null && (boolean) hasTransferFailed) {
            exchange.setProperty(TRANSACTION_FAILED, true);
            // TODO: SAVE ERROR CODE
        } else {
            exchange.setProperty(TRANSACTION_FAILED, false);
            // TODO: SERVER ID SAVING
        }
    }
}
