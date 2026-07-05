package co.com.nequi.sqs.sender.expiry;

import java.util.List;

record ReservationExpiryMessage(
        String orderId,
        List<String> ticketIds
) {
}
