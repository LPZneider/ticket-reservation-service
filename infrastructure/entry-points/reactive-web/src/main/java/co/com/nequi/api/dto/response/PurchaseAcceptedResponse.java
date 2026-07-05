package co.com.nequi.api.dto.response;

public record PurchaseAcceptedResponse(
        String orderId,
        String orderStatus
) {
}
