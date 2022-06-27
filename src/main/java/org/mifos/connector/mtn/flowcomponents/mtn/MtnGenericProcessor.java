package org.mifos.connector.mtn.flowcomponents.mtn;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import static org.mifos.connector.mtn.camel.config.CamelProperties.MPESA_API_RESPONSE;

@Component
public class MtnGenericProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        exchange.setProperty(MPESA_API_RESPONSE, exchange.getIn().getBody(String.class));
    }

}