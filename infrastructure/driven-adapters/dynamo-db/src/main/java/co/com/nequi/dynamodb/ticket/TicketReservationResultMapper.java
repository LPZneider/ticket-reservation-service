package co.com.nequi.dynamodb.ticket;

import co.com.nequi.model.ticket.Ticket;
import co.com.nequi.model.ticket.TicketReservationResult;
import co.com.nequi.model.ticket.TicketStatus;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Maps a reservation attempt's outcome to a domain result: the transaction succeeding
 * builds the reserved Ticket list; a TransactionCanceledException is inspected item by
 * item to find which ticketIds actually failed their ConditionExpression (the others
 * may have cancellation reason "None" — they weren't the cause of the failure).
 */
final class TicketReservationResultMapper {

    private static final String CONDITIONAL_CHECK_FAILED = "ConditionalCheckFailed";

    private TicketReservationResultMapper() {
    }

    static TicketReservationResult toSuccess(String eventId, List<String> ticketIds, String orderId,
                                              Instant reservedAt, Instant expiresAt) {
        List<Ticket> reservedTickets = ticketIds.stream()
                .map(ticketId -> Ticket.builder()
                        .ticketId(ticketId)
                        .eventId(eventId)
                        .status(TicketStatus.RESERVED)
                        .orderId(orderId)
                        .reservedAt(reservedAt)
                        .reservationExpiresAt(expiresAt)
                        .build())
                .toList();
        return new TicketReservationResult.Success(reservedTickets);
    }

    static TicketReservationResult toFailure(List<String> ticketIds, TransactionCanceledException ex) {
        List<CancellationReason> reasons = ex.cancellationReasons();
        List<String> unavailableTicketIds = IntStream.range(0, reasons.size())
                .filter(i -> CONDITIONAL_CHECK_FAILED.equals(reasons.get(i).code()))
                .mapToObj(ticketIds::get)
                .toList();
        return new TicketReservationResult.Failure(unavailableTicketIds);
    }
}
