package org.mifos.connector.mtn.utility;

import org.mifos.connector.common.channel.dto.TransactionChannelC2BRequestDTO;
import org.mifos.connector.mtn.dto.Payer;
import org.mifos.connector.mtn.dto.PaymentRequestDto;
import org.springframework.stereotype.Component;

/**
 * Mtn utilities class.
 */
@Component
public class MtnUtils {

    /**
     * Channel request converter.
     *
     * @param transactionChannelRequestDto
     *            transaction request dto
     *
     * @param transactionId
     *            transactionId
     * @return PaymentRequestDto
     */
    public PaymentRequestDto channelRequestConvertor(TransactionChannelC2BRequestDTO transactionChannelRequestDto,
            String transactionId) {
        String phoneNumber = transactionChannelRequestDto.getPayer()[0].getValue();
        if (phoneNumber.startsWith("+")) {
            phoneNumber = phoneNumber.substring(1);
        }
        PaymentRequestDto paymentRequestDto = new PaymentRequestDto();
        paymentRequestDto.setAmount(transactionChannelRequestDto.getAmount().getAmount().trim());
        paymentRequestDto.setCurrency(transactionChannelRequestDto.getAmount().getCurrency());
        paymentRequestDto.setExternalId(transactionId);
        paymentRequestDto.setPayer(new Payer(transactionChannelRequestDto.getPayer()[0].getKey(), phoneNumber));
        paymentRequestDto.setPayerMessage(transactionChannelRequestDto.getPayer()[1].getValue());
        paymentRequestDto.setPayeeNote(transactionChannelRequestDto.getPayer()[1].getValue());
        return paymentRequestDto;
    }
}
