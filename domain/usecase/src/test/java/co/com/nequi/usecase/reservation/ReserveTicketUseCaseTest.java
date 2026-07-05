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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReserveTicketUseCaseTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PurchaseRequestPublisher purchaseRequestPublisher;
    @Mock
    private ReservationExpiryPublisher reservationExpiryPublisher;

    private ReserveTicketUseCase useCase;

    private static final String EVENT_ID = "event-1";
    private static final List<String> TICKET_IDS = List.of("t1", "t2");
    private static final String USER_ID = "user-1";

    @BeforeEach
    void setUp() {
        useCase = new ReserveTicketUseCase(ticketRepository, orderRepository, purchaseRequestPublisher, reservationExpiryPublisher);
    }

    @Test
    void shouldReserveTicketsSaveOrderAndPublishAfterSuccessfulWrite() {
        List<Ticket> reservedTickets = TICKET_IDS.stream()
                .map(id -> Ticket.builder().ticketId(id).eventId(EVENT_ID).status(TicketStatus.RESERVED).build())
                .toList();

        when(ticketRepository.reserveTickets(anyString(), anyList(), anyString()))
                .thenReturn(Mono.just(new TicketReservationResult.Success(reservedTickets)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(purchaseRequestPublisher.publish(anyString(), anyString(), anyList(), anyString(), any(Instant.class)))
                .thenReturn(Mono.empty());
        when(reservationExpiryPublisher.publish(anyString(), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.reserve(EVENT_ID, TICKET_IDS, USER_ID))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(ReservationResult.Success.class);
                    ReservationResult.Success success = (ReservationResult.Success) result;
                    Order order = success.order();
                    assertThat(order.getOrderId()).isNotBlank();
                    assertThat(order.getEventId()).isEqualTo(EVENT_ID);
                    assertThat(order.getTicketIds()).isEqualTo(TICKET_IDS);
                    assertThat(order.getUserId()).isEqualTo(USER_ID);
                    assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMATION);
                    assertThat(success.purchaseRequestPublished()).isTrue();
                    assertThat(success.reservationExpiryPublished()).isTrue();
                })
                .verifyComplete();

        verify(orderRepository).save(any(Order.class));
        verify(purchaseRequestPublisher).publish(anyString(), anyString(), anyList(), anyString(), any(Instant.class));
        verify(reservationExpiryPublisher).publish(anyString(), anyList());
    }

    @Test
    void shouldStillReturnSuccessWhenPurchaseRequestPublishFailsButExpiryPublishSucceeds() {
        List<Ticket> reservedTickets = TICKET_IDS.stream()
                .map(id -> Ticket.builder().ticketId(id).eventId(EVENT_ID).status(TicketStatus.RESERVED).build())
                .toList();

        when(ticketRepository.reserveTickets(anyString(), anyList(), anyString()))
                .thenReturn(Mono.just(new TicketReservationResult.Success(reservedTickets)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(purchaseRequestPublisher.publish(anyString(), anyString(), anyList(), anyString(), any(Instant.class)))
                .thenReturn(Mono.error(new RuntimeException("SQS unavailable after retries")));
        when(reservationExpiryPublisher.publish(anyString(), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.reserve(EVENT_ID, TICKET_IDS, USER_ID))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(ReservationResult.Success.class);
                    ReservationResult.Success success = (ReservationResult.Success) result;
                    assertThat(success.purchaseRequestPublished()).isFalse();
                    assertThat(success.reservationExpiryPublished()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldStillReturnSuccessWhenBothPublishesFailAfterRetries() {
        List<Ticket> reservedTickets = TICKET_IDS.stream()
                .map(id -> Ticket.builder().ticketId(id).eventId(EVENT_ID).status(TicketStatus.RESERVED).build())
                .toList();

        when(ticketRepository.reserveTickets(anyString(), anyList(), anyString()))
                .thenReturn(Mono.just(new TicketReservationResult.Success(reservedTickets)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(purchaseRequestPublisher.publish(anyString(), anyString(), anyList(), anyString(), any(Instant.class)))
                .thenReturn(Mono.error(new RuntimeException("SQS unavailable after retries")));
        when(reservationExpiryPublisher.publish(anyString(), anyList()))
                .thenReturn(Mono.error(new RuntimeException("SQS unavailable after retries")));

        StepVerifier.create(useCase.reserve(EVENT_ID, TICKET_IDS, USER_ID))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(ReservationResult.Success.class);
                    ReservationResult.Success success = (ReservationResult.Success) result;
                    assertThat(success.purchaseRequestPublished()).isFalse();
                    assertThat(success.reservationExpiryPublished()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnFailureWithoutPersistingOrPublishingWhenTicketsUnavailable() {
        List<String> unavailable = List.of("t2");
        when(ticketRepository.reserveTickets(anyString(), anyList(), anyString()))
                .thenReturn(Mono.just(new TicketReservationResult.Failure(unavailable)));

        StepVerifier.create(useCase.reserve(EVENT_ID, TICKET_IDS, USER_ID))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(ReservationResult.Failure.class);
                    assertThat(((ReservationResult.Failure) result).unavailableTicketIds()).isEqualTo(unavailable);
                })
                .verifyComplete();

        verify(orderRepository, never()).save(any());
        verify(purchaseRequestPublisher, never()).publish(anyString(), anyString(), anyList(), anyString(), any());
        verify(reservationExpiryPublisher, never()).publish(anyString(), anyList());
    }
}
