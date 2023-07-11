package org.mifos.connector.mtn.dto;

import lombok.Data;

@Data
public class PaymentRequestDTO {

    private String amount;
    private String currency;
    private String externalId;
    private String payerMessage;
    private String payeeNote;
    private Payer payer;
}
