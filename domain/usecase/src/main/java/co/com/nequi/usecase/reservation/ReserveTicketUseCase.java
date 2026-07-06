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

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ReserveTicketUseCase {

    private final TicketRepository ticketRepository;
    private final OrderRepository orderRepository;
    private final PurchaseRequestPublisher purchaseRequestPublisher;
    private final ReservationExpiryPublisher reservationExpiryPublisher;

    public Mono<ReservationResult> reserve(String eventId, int quantity, String userId) {
        String orderId = UUID.randomUUID().toString();
        return ticketRepository.reserveAndCreateTickets(eventId, quantity, orderId)
                .flatMap(result -> switch (result) {
                    case TicketReservationResult.Failure f ->
                            Mono.just((ReservationResult) new ReservationResult.Failure(f.reason()));
                    case TicketReservationResult.Success s ->
                            confirmReservation(orderId, eventId, s, userId);
                });
    }

    private Mono<ReservationResult> confirmReservation(String orderId, String eventId,
                                                        TicketReservationResult.Success result, String userId) {
        var ticketIds = result.reservedTickets().stream()
                .map(t -> t.getTicketId())
                .toList();

        Order order = Order.builder()
                .orderId(orderId)
                .eventId(eventId)
                .ticketIds(ticketIds)
                .userId(userId)
                .orderStatus(OrderStatus.PENDING_CONFIRMATION)
                .createdAt(Instant.now())
                .build();

        return orderRepository.save(order)
                .flatMap(saved -> publishMessages(saved, eventId, ticketIds, userId)
                        .map(o -> (ReservationResult) new ReservationResult.Success(
                                saved, o.purchaseOk(), o.expiryOk())));
    }

    private Mono<PublishOutcome> publishMessages(Order order, String eventId,
                                                  java.util.List<String> ticketIds, String userId) {
        Mono<Boolean> purchaseOk = purchaseRequestPublisher
                .publish(order.getOrderId(), eventId, ticketIds, userId, order.getCreatedAt())
                .thenReturn(true).onErrorReturn(false);
        Mono<Boolean> expiryOk = reservationExpiryPublisher
                .publish(order.getOrderId(), ticketIds)
                .thenReturn(true).onErrorReturn(false);
        return Mono.zip(purchaseOk, expiryOk).map(t -> new PublishOutcome(t.getT1(), t.getT2()));
    }

    private record PublishOutcome(boolean purchaseOk, boolean expiryOk) {}
}
