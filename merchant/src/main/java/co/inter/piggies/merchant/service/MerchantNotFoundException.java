package co.inter.piggies.merchant.service;

public class MerchantNotFoundException extends DomainException {

    public MerchantNotFoundException(String merchantId) {
        super(404, "Merchant not found", "Merchant desconhecido: " + merchantId);
    }
}
