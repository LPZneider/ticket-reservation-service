package co.com.nequi.model.ticket.gateways;

import co.com.nequi.model.ticket.TicketReservationResult;
import reactor.core.publisher.Mono;

import java.util.List;

public interface TicketRepository {

    Mono<TicketReservationResult> reserveTickets(String eventId, List<String> ticketIds, String orderId);
}
