package org.mifos.connector.mtn.Utility;
import org.mifos.connector.common.channel.dto.TransactionChannelC2BRequestDTO;
import org.mifos.connector.mtn.dto.Payer;
import org.mifos.connector.mtn.dto.PaymentRequestDTO;
import org.springframework.stereotype.Component;

@Component
public class MtnUtils {

    public PaymentRequestDTO channelRequestConvertor(TransactionChannelC2BRequestDTO transactionChannelRequestDTO, String transactionId) {
        PaymentRequestDTO paymentRequestDTO = new PaymentRequestDTO();
        paymentRequestDTO.setAmount(transactionChannelRequestDTO.getAmount().getAmount().trim());
        paymentRequestDTO.setCurrency(transactionChannelRequestDTO.getAmount().getCurrency());
        paymentRequestDTO.setExternalId(transactionId);
        paymentRequestDTO.setPayer(new Payer(transactionChannelRequestDTO.getPayer()[0].getKey(),transactionChannelRequestDTO.getPayer()[0].getValue()));
        return paymentRequestDTO;
    }
}
