package co.com.nequi.model.reservation;

import co.com.nequi.model.order.Order;

import java.util.List;

public sealed interface ReservationResult {

    /**
     * purchaseRequestPublished / reservationExpiryPublished: whether each publish
     * succeeded after the adapter's own retries. A reservation is still a success
     * (202) even if one or both publishes ultimately failed — the DynamoDB writes
     * already committed. If reservationExpiryPublished is true, that message is a
     * fail-safe: the order will still expire and release the tickets even if
     * purchase-requests was never delivered.
     */
    record Success(Order order, boolean purchaseRequestPublished,
                   boolean reservationExpiryPublished) implements ReservationResult {
    }

    record Failure(List<String> unavailableTicketIds) implements ReservationResult {
    }
}
