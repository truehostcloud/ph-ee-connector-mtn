package org.mifos.connector.mtn.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Payer model.
 */
@Setter
@Getter
public class Payer {

    @JsonProperty("partyIdType")
    private String partyIdType;
    @JsonProperty("partyId")
    private String partyId;

    public Payer() {

    }

    public Payer(String partyIdType, String partyId) {
        this.partyIdType = partyIdType;
        this.partyId = partyId;
    }

    @Override
    public String toString() {
        return "Payer{" + "partyIdType='" + partyIdType + '\'' + ", partyId='" + partyId + '\'' + '}';
    }
}
