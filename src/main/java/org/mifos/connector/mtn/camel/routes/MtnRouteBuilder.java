package org.mifos.connector.mtn.camel.routes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.json.JSONObject;
import org.mifos.connector.mtn.Utility.ConnectionUtils;
import org.mifos.connector.mtn.Utility.MtnProps;
import org.mifos.connector.mtn.dto.MtnCallback;
import org.mifos.connector.mtn.dto.PaymentRequestDTO;
import org.mifos.connector.mtn.flowcomponents.mtn.MtnGenericProcessor;
import org.mifos.connector.mtn.flowcomponents.transaction.CollectionResponseProcessor;
import org.mifos.connector.mtn.flowcomponents.transaction.TransactionResponseProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.mifos.connector.mtn.auth.AccessTokenStore;

import static org.mifos.connector.mtn.camel.config.CamelProperties.*;
import static org.mifos.connector.mtn.zeebe.ZeebeVariables.*;

@Component
public class MtnRouteBuilder extends RouteBuilder {


    private final AccessTokenStore accessTokenStore;
    private TransactionResponseProcessor transactionResponseProcessor;
    private MtnProps mtnProps;
    @Value("${mtn.api.timeout}")
    private Integer  mtnTimeout;
    private MtnGenericProcessor mtnGenericProcessor;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ObjectMapper objectMapper;
    private CollectionResponseProcessor collectionResponseProcessor;
    @Value("${mtn.max-retry-count}")
    private Integer maxRetryCount;

    public MtnRouteBuilder(AccessTokenStore accessTokenStore, TransactionResponseProcessor transactionResponseProcessor,MtnProps mtnRwProp, MtnGenericProcessor mtnGenericProcessor, ObjectMapper objectMapper, CollectionResponseProcessor collectionResponseProcessor) {
        this.accessTokenStore = accessTokenStore;
        this.transactionResponseProcessor = transactionResponseProcessor;
        this.mtnProps = mtnRwProp;
        this.mtnGenericProcessor = mtnGenericProcessor;
        this.objectMapper = objectMapper;
        this.collectionResponseProcessor =  collectionResponseProcessor;
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


        /*
           Use this endpoint for receiving the callback from MTN endpoint
         */
        from("rest:POST:/buygoods/callback")
                .id("mtn-buy-goods-callback")
                .log(LoggingLevel.INFO, "Callback body \n\n..\n\n..\n\n.. ${body}")
                .to("direct:mtn-callback-handler");

        from("direct:mtn-callback-handler")
                .id("mtn-callback-handler")
                .log(LoggingLevel.INFO, "Handling callback body")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    MtnCallback callback = objectMapper.readValue(
                            body, MtnCallback.class);
                    exchange.setProperty(TRANSACTION_ID, callback.getExternalId());
                    // TODO: SAVE SERVER ID ?
                    logger.info("\n\n MTN Callback " + callback + "\n");
                    logger.info("\n\n Correlation Key " + callback.getExternalId());
                    if(callback.getStatus().equals("SUCCESSFUL")) {
                        exchange.setProperty(TRANSACTION_FAILED, false);
                        exchange.setProperty(CALLBACK_RECEIVED, true);
                        exchange.setProperty(CALLBACK, callback.toString());
                    } else {
                        exchange.setProperty(TRANSACTION_FAILED, true);
                        // TODO: SAVE ERROR CODE AND INFO
                    }
                })
                .log(LoggingLevel.INFO, "After Handling callback body")
                .process(collectionResponseProcessor);

        /*
         * Starts the payment flow
         *
         * Step1: Authenticate the user by initiating [get-access-token] flow
         * Step2: On successful [Step1], directs to [mtn-buy-goods] flow
         */
        from("direct:mtn-get-transaction-status-base")
                .id("mtn-buy-goods-get-transaction-status-base")
                .log(LoggingLevel.INFO, "Starting buy goods transaction status flow")
                .choice()
                .when(exchangeProperty(SERVER_TRANSACTION_STATUS_RETRY_COUNT).isLessThanOrEqualTo(maxRetryCount))
                .to("direct:get-access-token")
                .process(exchange -> exchange.setProperty(ACCESS_TOKEN, accessTokenStore.getAccessToken()))
                .log(LoggingLevel.INFO, "Got access token, moving on to API call.")
                .to("direct:mtn-transaction-status")
                .log(LoggingLevel.INFO, "Status: ${header.CamelHttpResponseCode}")
                .log(LoggingLevel.INFO, "Transaction API response: ${body}")
                .to("direct:mtn-transaction-status-response-handler")
                .otherwise()
                .process(exchange -> {
                    exchange.setProperty(IS_RETRY_EXCEEDED, true);
                    exchange.setProperty(TRANSACTION_FAILED, true);
                })
                .process(collectionResponseProcessor);

        /*
         * Takes the request for transaction status and forwards in to the mtn transaction status endpoint
         */
        from("direct:mtn-transaction-status")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .setHeader("Content-Type", constant("application/json"))
                .setHeader("Ocp-Apim-Subscription-Key", constant(mtnProps.getSubscriptionKey()))
                .setHeader("X-Callback-Url", constant(mtnProps.getCallBack()))
                .setHeader("X-Reference-Id", simple( "${exchangeProperty."+CORRELATION_ID+"}"))
                .setHeader("X-Target-Environment", constant(mtnProps.getEnvironment()))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty."+ACCESS_TOKEN+"}"))
                .marshal().json(JsonLibrary.Jackson)
                .toD(mtnProps.getApiHost() + "/collection/v1_0/requesttopay/" + "${exchangeProperty."+TRANSACTION_ID+"}" + "?bridgeEndpoint=true&throwExceptionOnFailure=false&"+
                        ConnectionUtils.getConnectionTimeoutDsl(mtnTimeout))
                .log(LoggingLevel.INFO, "MTN-RW STATUS called, response: \n\n ${body}");

        /*
         * Route to handle async transaction status API responses
         */
        from("direct:mtn-transaction-status-response-handler")
                .id("mtn-transaction-status-response-handler")
                .log(LoggingLevel.INFO, "## Starting MTN transaction status handler route")
                .choice()
                .when(header(Exchange.HTTP_RESPONSE_CODE).isEqualTo("200"))
                .log(LoggingLevel.INFO, "Transaction status request successful")
                .process (exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    JSONObject jsonObject = new JSONObject(body);
                    exchange.setProperty(LAST_RESPONSE_BODY, body);
                    if(jsonObject.getString("status").equals("SUCCESSFUL")) {
                        exchange.setProperty(TRANSACTION_FAILED, false);
                    }
                    else {
                        exchange.setProperty(IS_TRANSACTION_PENDING, true);
                    }
                })
                .process(collectionResponseProcessor)
                .otherwise()
                .log(LoggingLevel.ERROR, "Transaction status request unsuccessful")
                .process(exchange -> {
                    Object correlationId = exchange.getProperty(CORRELATION_ID);
                    exchange.setProperty(TRANSACTION_ID, correlationId);
                })
                .setProperty(TRANSACTION_FAILED, constant(true))
                .process(collectionResponseProcessor);
    }
}