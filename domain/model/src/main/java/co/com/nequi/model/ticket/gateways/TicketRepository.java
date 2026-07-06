package co.com.nequi.model.ticket.gateways;

import co.com.nequi.model.ticket.TicketReservationResult;
import reactor.core.publisher.Mono;

public interface TicketRepository {

    Mono<TicketReservationResult> reserveAndCreateTickets(String eventId, int quantity, String orderId);
}
