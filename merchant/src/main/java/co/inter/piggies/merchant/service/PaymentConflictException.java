package co.inter.piggies.merchant.service;

import java.util.UUID;

/** Mesmo paymentId reutilizado com merchant/valor diferentes do recebível já gravado. */
public class PaymentConflictException extends DomainException {

    public PaymentConflictException(UUID paymentId) {
        super(409, "Payment conflict",
                "paymentId " + paymentId + " já foi creditado com merchant/valor diferentes");
    }
}
