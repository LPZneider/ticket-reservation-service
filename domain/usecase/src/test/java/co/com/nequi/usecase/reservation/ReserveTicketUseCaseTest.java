package co.com.nequi.usecase.reservation;

import co.com.nequi.model.order.Order;
import co.com.nequi.model.order.OrderStatus;
import co.com.nequi.model.order.gateways.OrderRepository;
import co.com.nequi.model.purchase.gateways.PurchaseRequestPublisher;
import co.com.nequi.model.reservation.ReservationResult;
import co.com.nequi.model.reservation.gateways.ReservationExpiryPublisher;
import co.com.nequi.model.ticket.Ticket;
import co.com.nequi.model.ticket.TicketReservationResult;
import co.com.nequi.model.ticket.TicketStatus;
import co.com.nequi.model.ticket.gateways.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReserveTicketUseCaseTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PurchaseRequestPublisher purchaseRequestPublisher;
    @Mock private ReservationExpiryPublisher reservationExpiryPublisher;

    private ReserveTicketUseCase useCase;

    private static final String EVENT_ID = "event-1";
    private static final String USER_ID  = "user-1";
    private static final int    QUANTITY = 2;

    @BeforeEach
    void setUp() {
        useCase = new ReserveTicketUseCase(ticketRepository, orderRepository,
                purchaseRequestPublisher, reservationExpiryPublisher);
    }

    private List<Ticket> reservedTickets(String orderId) {
        return List.of(
                Ticket.builder().ticketId(orderId + "-1").eventId(EVENT_ID)
                        .status(TicketStatus.RESERVED).orderId(orderId).version(0).build(),
                Ticket.builder().ticketId(orderId + "-2").eventId(EVENT_ID)
                        .status(TicketStatus.RESERVED).orderId(orderId).version(0).build());
    }

    @Test
    void shouldSaveOrderWithGeneratedTicketIdsAndPublishBothMessages() {
        when(ticketRepository.reserveAndCreateTickets(anyString(), anyInt(), anyString()))
                .thenAnswer(inv -> {
                    String orderId = inv.getArgument(2);
                    return Mono.just(new TicketReservationResult.Success(reservedTickets(orderId)));
                });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(purchaseRequestPublisher.publish(anyString(), anyString(), anyList(), anyString(), any(Instant.class)))
                .thenReturn(Mono.empty());
        when(reservationExpiryPublisher.publish(anyString(), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.reserve(EVENT_ID, QUANTITY, USER_ID))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(ReservationResult.Success.class);
                    ReservationResult.Success success = (ReservationResult.Success) result;
                    assertThat(success.order().getOrderId()).isNotBlank();
                    assertThat(success.order().getEventId()).isEqualTo(EVENT_ID);
                    assertThat(success.order().getTicketIds()).hasSize(2);
                    assertThat(success.order().getOrderStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMATION);
                    assertThat(success.purchaseRequestPublished()).isTrue();
                    assertThat(success.reservationExpiryPublished()).isTrue();
                })
                .verifyComplete();

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getTicketIds()).hasSize(2);
        assertThat(orderCaptor.getValue().getTicketIds().get(0)).endsWith("-1");
    }

    @Test
    void shouldPassGeneratedTicketIdsToSqsPublishers() {
        when(ticketRepository.reserveAndCreateTickets(anyString(), anyInt(), anyString()))
                .thenAnswer(inv -> {
                    String orderId = inv.getArgument(2);
                    return Mono.just(new TicketReservationResult.Success(reservedTickets(orderId)));
                });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(purchaseRequestPublisher.publish(anyString(), anyString(), anyList(), anyString(), any(Instant.class)))
                .thenReturn(Mono.empty());
        when(reservationExpiryPublisher.publish(anyString(), anyList())).thenReturn(Mono.empty());

        useCase.reserve(EVENT_ID, QUANTITY, USER_ID).block();

        ArgumentCaptor<List<String>> ticketCaptor = ArgumentCaptor.forClass(List.class);
        verify(purchaseRequestPublisher).publish(anyString(), anyString(), ticketCaptor.capture(), anyString(), any());
        assertThat(ticketCaptor.getValue()).hasSize(2);
        assertThat(ticketCaptor.getValue().get(0)).endsWith("-1");
    }

    @Test
    void shouldReturnSuccessWithPurchaseFalseWhenPurchasePublishFails() {
        when(ticketRepository.reserveAndCreateTickets(anyString(), anyInt(), anyString()))
                .thenAnswer(inv -> Mono.just(new TicketReservationResult.Success(
                        reservedTickets(inv.getArgument(2)))));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(purchaseRequestPublisher.publish(anyString(), anyString(), anyList(), anyString(), any()))
                .thenReturn(Mono.error(new RuntimeException("SQS down")));
        when(reservationExpiryPublisher.publish(anyString(), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.reserve(EVENT_ID, QUANTITY, USER_ID))
                .assertNext(result -> {
                    ReservationResult.Success s = (ReservationResult.Success) result;
                    assertThat(s.purchaseRequestPublished()).isFalse();
                    assertThat(s.reservationExpiryPublished()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnSuccessWithBothFalseWhenBothPublishesFail() {
        when(ticketRepository.reserveAndCreateTickets(anyString(), anyInt(), anyString()))
                .thenAnswer(inv -> Mono.just(new TicketReservationResult.Success(
                        reservedTickets(inv.getArgument(2)))));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(purchaseRequestPublisher.publish(anyString(), anyString(), anyList(), anyString(), any()))
                .thenReturn(Mono.error(new RuntimeException("SQS down")));
        when(reservationExpiryPublisher.publish(anyString(), anyList()))
                .thenReturn(Mono.error(new RuntimeException("SQS down")));

        StepVerifier.create(useCase.reserve(EVENT_ID, QUANTITY, USER_ID))
                .assertNext(result -> {
                    ReservationResult.Success s = (ReservationResult.Success) result;
                    assertThat(s.purchaseRequestPublished()).isFalse();
                    assertThat(s.reservationExpiryPublished()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnFailureWithoutPersistingWhenNotEnoughAvailability() {
        when(ticketRepository.reserveAndCreateTickets(anyString(), anyInt(), anyString()))
                .thenReturn(Mono.just(new TicketReservationResult.Failure("Not enough available tickets")));

        StepVerifier.create(useCase.reserve(EVENT_ID, QUANTITY, USER_ID))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(ReservationResult.Failure.class);
                    assertThat(((ReservationResult.Failure) result).reason()).contains("Not enough");
                })
                .verifyComplete();

        verify(orderRepository, never()).save(any());
        verify(purchaseRequestPublisher, never()).publish(anyString(), anyString(), anyList(), anyString(), any());
        verify(reservationExpiryPublisher, never()).publish(anyString(), anyList());
    }
}
