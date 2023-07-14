package org.mifos.connector.mtn.zeebe;

/**
 * Holds variables to be used by Zeebe.
 */
public class ZeebeVariables {

    public static final String TRANSACTION_ID = "transactionId";
    public static final String TRANSACTION_FAILED = "transactionFailed";
    public static final String CALLBACK_RECEIVED = "isCallbackReceived";
    public static final String CALLBACK = "callback";
    public static final String TRANSFER_MESSAGE = "transaction-request";
    public static final String SERVER_TRANSACTION_STATUS_RETRY_COUNT = "mpesaTransactionStatusRetryCount";
    public static final String ZEEBE_ELEMENT_INSTANCE_KEY = "elementInstanceKey";
    public static final String TIMER = "timer";
    public static final String GET_TRANSACTION_STATUS_RESPONSE = "getTransactionStatusResponse";
    public static final String GET_TRANSACTION_STATUS_RESPONSE_CODE = "getTransactionStatusHttpCode";

    public static final String EXTERNAL_ID = "externalId";
    public static final String FINANCIAL_TRANSACTION_ID = "financialTransactionId";
}
