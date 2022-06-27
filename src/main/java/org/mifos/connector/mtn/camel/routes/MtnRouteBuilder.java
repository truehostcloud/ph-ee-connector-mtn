package org.mifos.connector.mtn.camel.routes;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.mtn.Utility.ConnectionUtils;
import org.mifos.connector.mtn.Utility.MtnProps;
import org.mifos.connector.mtn.dto.PaymentRequestDTO;
import org.mifos.connector.mtn.flowcomponents.mtn.MtnGenericProcessor;
import org.mifos.connector.mtn.flowcomponents.transaction.TransactionResponseProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.mifos.connector.mtn.auth.AccessTokenStore;

import static org.mifos.connector.mtn.camel.config.CamelProperties.*;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.TRANSACTION_FAILED;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.TRANSACTION_ID;

@Component
public class MtnRouteBuilder extends RouteBuilder {


    private final AccessTokenStore accessTokenStore;
    private TransactionResponseProcessor transactionResponseProcessor;
    private MtnProps mtnProps;
    @Value("${mtn.api.timeout}")
    private Integer  mtnTimeout;

    private MtnGenericProcessor mtnGenericProcessor;

    public MtnRouteBuilder(AccessTokenStore accessTokenStore, TransactionResponseProcessor transactionResponseProcessor,MtnProps mtnRwProp, MtnGenericProcessor mtnGenericProcessor) {
        this.accessTokenStore = accessTokenStore;
        this.transactionResponseProcessor = transactionResponseProcessor;
        this.mtnProps = mtnRwProp;
        this.mtnGenericProcessor = mtnGenericProcessor;
    }


    @Override
    public void configure() {
        /*
         * Starts the payment flow
         *
         * Step1: Authenticate the user by initiating [get-access-token] flow
         * Step2: On successful [Step1], directs to [mtn-transaction-response-handler] flow
         */
        from("direct:request-to-pay-base")
                .id("request-to-pay-base")
                .log(LoggingLevel.INFO, "Starting buy goods flow")
                .log(LoggingLevel.INFO, "Starting buy goods flow with retry count: " + 3)
                .to("direct:get-access-token")
                .process(exchange -> exchange.setProperty(ACCESS_TOKEN, accessTokenStore.getAccessToken()))
                .log(LoggingLevel.INFO, "Got access token, moving on to API call.")
                .to("direct:request-to-pay")
                .log(LoggingLevel.INFO, "Status: ${header.CamelHttpResponseCode}")
                .to("direct:mtn-transaction-response-handler");
        /*
         * Takes the access token and payment request and forwards the requests to Mtn API.
         * [Password] and [X-Callback-Url] [Ocp-Apim-Subscription-Key] [X-Target-Environment] are set in runtime and request is forwarded to MTN endpoint.
         */
        from("direct:request-to-pay")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("Content-Type", constant("application/json"))
                .setHeader("Ocp-Apim-Subscription-Key", constant(mtnProps.getSubscriptionKey()))
                .setHeader("X-Callback-Url", constant(mtnProps.getCallBack()))
                .setHeader("X-Reference-Id", simple( "${exchangeProperty."+CORRELATION_ID+"}"))
                .setHeader("X-Target-Environment", constant(mtnProps.getEnvironment()))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty."+ACCESS_TOKEN+"}"))
                .setBody(exchange -> {
                    PaymentRequestDTO paymentRequestDTO =
                            (PaymentRequestDTO) exchange.getProperty(BUY_GOODS_REQUEST_BODY);
                    return paymentRequestDTO;
                })
                .marshal().json(JsonLibrary.Jackson)
                .toD(mtnProps.getApiHost() + "/collection/v1_0/requesttopay" +"?bridgeEndpoint=true&throwExceptionOnFailure=false&"+
                        ConnectionUtils.getConnectionTimeoutDsl(mtnTimeout))
                .process(mtnGenericProcessor);

        from("direct:mtn-transaction-response-handler")
                .id("mtn-transaction-response-handler")
                .choice()
                .when(header(Exchange.HTTP_RESPONSE_CODE).isEqualTo("202"))
                .log(LoggingLevel.INFO, "MTN Collection request successful")
                .process(exchange -> {
                    exchange.setProperty(TRANSACTION_ID, exchange.getProperty(CORRELATION_ID));
                    // TODO: Deal with server ID
                })
                .process(transactionResponseProcessor)
                .otherwise()
                .log(LoggingLevel.ERROR, "MTN Collection request unsuccessful")
                .process(exchange -> {
                    Object correlationId = exchange.getProperty(CORRELATION_ID);
                    exchange.setProperty(TRANSACTION_ID, correlationId);
                })
                .setProperty(TRANSACTION_FAILED, constant(true))
                .process(transactionResponseProcessor);

    }
}
