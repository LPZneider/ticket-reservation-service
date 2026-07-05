package co.com.nequi.sqs.sender.purchase;

import java.util.List;

record PurchaseRequestMessage(
        String orderId,
        String eventId,
        List<String> ticketIds,
        String userId,
        String requestedAt
) {
}
