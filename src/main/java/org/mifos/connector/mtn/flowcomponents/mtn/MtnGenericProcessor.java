package org.mifos.connector.mtn.flowcomponents.mtn;

import static org.mifos.connector.mtn.camel.config.CamelProperties.MTN_API_RESPONSE;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

@Component
public class MtnGenericProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        exchange.setProperty(MTN_API_RESPONSE, exchange.getIn().getBody(String.class));
    }

}
