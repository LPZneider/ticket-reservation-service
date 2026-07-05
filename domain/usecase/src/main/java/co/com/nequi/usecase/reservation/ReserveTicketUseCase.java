package co.com.nequi.usecase.reservation;

import co.com.nequi.model.order.Order;
import co.com.nequi.model.order.OrderStatus;
import co.com.nequi.model.order.gateways.OrderRepository;
import co.com.nequi.model.purchase.gateways.PurchaseRequestPublisher;
import co.com.nequi.model.reservation.ReservationResult;
import co.com.nequi.model.reservation.gateways.ReservationExpiryPublisher;
import co.com.nequi.model.ticket.TicketReservationResult;
import co.com.nequi.model.ticket.gateways.TicketRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ReserveTicketUseCase {

    private final TicketRepository ticketRepository;
    private final OrderRepository orderRepository;
    private final PurchaseRequestPublisher purchaseRequestPublisher;
    private final ReservationExpiryPublisher reservationExpiryPublisher;

    public Mono<ReservationResult> reserve(String eventId, List<String> ticketIds, String userId) {
        String orderId = UUID.randomUUID().toString();
        return ticketRepository.reserveTickets(eventId, ticketIds, orderId)
                .flatMap(result -> switch (result) {
                    case TicketReservationResult.Failure failure ->
                            Mono.<ReservationResult>just(new ReservationResult.Failure(failure.unavailableTicketIds()));
                    case TicketReservationResult.Success ignored ->
                            confirmReservation(orderId, eventId, ticketIds, userId);
                });
    }

    private Mono<ReservationResult> confirmReservation(String orderId, String eventId, List<String> ticketIds, String userId) {
        Order order = Order.builder()
                .orderId(orderId)
                .eventId(eventId)
                .ticketIds(ticketIds)
                .userId(userId)
                .orderStatus(OrderStatus.PENDING_CONFIRMATION)
                .createdAt(Instant.now())
                .build();

        return orderRepository.save(order)
                .flatMap(savedOrder -> publishReservationMessages(savedOrder, eventId, ticketIds, userId)
                        .map(publishOutcome -> (ReservationResult) new ReservationResult.Success(
                                savedOrder, publishOutcome.getT1(), publishOutcome.getT2())));
    }

    /**
     * Each publish already retried independently inside its own adapter; here we
     * only record whether it ultimately succeeded. A failure on either (or both)
     * never fails the reservation itself — the DynamoDB writes already committed,
     * so the client still gets 202. The entry-point decides what to log based on
     * which combination of outcomes it receives.
     */
    private Mono<Tuple2<Boolean, Boolean>> publishReservationMessages(Order order, String eventId,
                                                                       List<String> ticketIds, String userId) {
        Mono<Boolean> purchasePublished = purchaseRequestPublisher
                .publish(order.getOrderId(), eventId, ticketIds, userId, order.getCreatedAt())
                .thenReturn(true)
                .onErrorReturn(false);
        Mono<Boolean> expiryPublished = reservationExpiryPublisher
                .publish(order.getOrderId(), ticketIds)
                .thenReturn(true)
                .onErrorReturn(false);

        return Mono.zip(purchasePublished, expiryPublished);
    }
}
