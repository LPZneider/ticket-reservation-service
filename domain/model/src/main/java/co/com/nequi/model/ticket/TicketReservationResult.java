package co.com.nequi.model.ticket;

import java.util.List;

public sealed interface TicketReservationResult {

    record Success(List<Ticket> reservedTickets) implements TicketReservationResult {
    }

    record Failure(List<String> unavailableTicketIds) implements TicketReservationResult {
    }
}
