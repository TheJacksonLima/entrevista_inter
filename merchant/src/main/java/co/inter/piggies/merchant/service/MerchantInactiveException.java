package co.inter.piggies.merchant.service;

public class MerchantInactiveException extends DomainException {

    public MerchantInactiveException(String merchantId) {
        super(409, "Merchant inactive", "Merchant inativo: " + merchantId);
    }
}
