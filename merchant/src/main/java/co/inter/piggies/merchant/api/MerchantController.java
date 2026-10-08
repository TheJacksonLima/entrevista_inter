package co.inter.piggies.merchant.api;

import co.inter.piggies.merchant.api.Dtos.MerchantStatusResponse;
import co.inter.piggies.merchant.api.Dtos.ReceivableResponse;
import co.inter.piggies.merchant.domain.Merchant;
import co.inter.piggies.merchant.service.ReceivableService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.List;

@Controller("/merchants/{merchantId}")
@io.micronaut.scheduling.annotation.ExecuteOn(io.micronaut.scheduling.TaskExecutors.BLOCKING)
public class MerchantController {

    private final ReceivableService service;

    public MerchantController(ReceivableService service) {
        this.service = service;
    }

    @Get("/status")
    public MerchantStatusResponse status(String merchantId) {
        Merchant m = service.getMerchant(merchantId);
        return new MerchantStatusResponse(m.getId(), m.isActive());
    }

    @Get("/receivables")
    public List<ReceivableResponse> receivables(String merchantId) {
        return service.listByMerchant(merchantId).stream().map(ReceivableResponse::from).toList();
    }
}
