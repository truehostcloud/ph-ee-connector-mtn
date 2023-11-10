package org.mifos.connector.mtn.dto;

import lombok.Data;

/**
 * DTO for making a request to MTN.
 */
@Data
public class PaymentRequestDto {

    private String amount;
    private String currency;
    private String externalId;
    private String payerMessage;
    private String payeeNote;
    private Payer payer;

    @Override
    public String toString() {
        return "PaymentRequestDto [amount=" + amount + ", currency=" + currency + ", externalId=" + externalId
                + ", payerMessage=" + payerMessage + ", payeeNote=" + payeeNote + ", payer=" + payer.toString() + "]";

    }
}
