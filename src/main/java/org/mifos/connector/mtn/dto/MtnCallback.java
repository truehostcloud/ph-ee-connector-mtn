package org.mifos.connector.mtn.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MtnCallback {
    @JsonProperty("financialTransactionId")
    private String financialTransactionId;
    @JsonProperty("externalId")
    private String externalId;
    @JsonProperty("amount")
    private String amount;
    @JsonProperty("currency")
    private String currency;
    @JsonProperty("payer")
    private Payer payer;
    @JsonProperty("partyIdType")
    private String partyIdType;
    @JsonProperty("MSISDN")
    private String msisdn;
    @JsonProperty("partyId")
    private String partyId;
    @JsonProperty("status")
    private String status;
}
