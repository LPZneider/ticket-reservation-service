package co.com.nequi.dynamodb.ticket;

import co.com.nequi.model.ticket.TicketReservationResult;
import co.com.nequi.model.ticket.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketDynamoDBAdapterTest {

    @Mock
    private DynamoDbAsyncClient client;

    private TicketDynamoDBAdapter adapter;

    private static final String EVENT_ID  = "event-1";
    private static final String ORDER_ID  = "order-1";
    private static final int    QUANTITY  = 3;

    @BeforeEach
    void setUp() {
        adapter = new TicketDynamoDBAdapter(client, "tickets");
    }

    @Test
    void shouldReturnSuccessWithNReservedTicketsWhenTransactionSucceeds() {
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));

        StepVerifier.create(adapter.reserveAndCreateTickets(EVENT_ID, QUANTITY, ORDER_ID))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(TicketReservationResult.Success.class);
                    var success = (TicketReservationResult.Success) result;
                    assertThat(success.reservedTickets()).hasSize(QUANTITY);
                    success.reservedTickets().forEach(t -> {
                        assertThat(t.getEventId()).isEqualTo(EVENT_ID);
                        assertThat(t.getOrderId()).isEqualTo(ORDER_ID);
                        assertThat(t.getStatus()).isEqualTo(TicketStatus.RESERVED);
                        assertThat(t.getReservedAt()).isNotNull();
                        assertThat(t.getReservationExpiresAt()).isNotNull();
                    });
                })
                .verifyComplete();
    }

    @Test
    void shouldBuildTransactionWithOneEventUpdatePlusNTicketPuts() {
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));

        adapter.reserveAndCreateTickets(EVENT_ID, QUANTITY, ORDER_ID).block();

        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(client).transactWriteItems(captor.capture());

        var items = captor.getValue().transactItems();
        // 1 Update on Event + QUANTITY Puts for tickets
        assertThat(items).hasSize(QUANTITY + 1);

        // First item must be the Event Update (sk=METADATA)
        var eventUpdate = items.get(0).update();
        assertThat(eventUpdate).isNotNull();
        assertThat(eventUpdate.key().get("sk").s()).isEqualTo("METADATA");
        assertThat(eventUpdate.updateExpression()).contains("availableCount");
        assertThat(eventUpdate.conditionExpression()).contains("availableCount >= :qty");

        // Remaining items must be ticket Puts
        for (int i = 1; i <= QUANTITY; i++) {
            var put = items.get(i).put();
            assertThat(put).isNotNull();
            assertThat(put.item().get("pk").s()).isEqualTo(EVENT_ID);
            assertThat(put.item().get("orderId").s()).isEqualTo(ORDER_ID);
            assertThat(put.item().get("status").s()).isEqualTo(TicketStatus.RESERVED.name());
            assertThat(put.item().get("ticketId").s()).isEqualTo(ORDER_ID + "-" + i);
        }
    }

    @Test
    void shouldGenerateTicketIdsAsOrderIdDashIndex() {
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));

        StepVerifier.create(adapter.reserveAndCreateTickets(EVENT_ID, 2, ORDER_ID))
                .assertNext(result -> {
                    var tickets = ((TicketReservationResult.Success) result).reservedTickets();
                    assertThat(tickets.get(0).getTicketId()).isEqualTo(ORDER_ID + "-1");
                    assertThat(tickets.get(1).getTicketId()).isEqualTo(ORDER_ID + "-2");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnFailureWithReasonWhenTransactionCancelled() {
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        TransactionCanceledException.builder().message("ConditionalCheckFailed").build()));

        StepVerifier.create(adapter.reserveAndCreateTickets(EVENT_ID, QUANTITY, ORDER_ID))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(TicketReservationResult.Failure.class);
                    assertThat(((TicketReservationResult.Failure) result).reason())
                            .contains("Not enough available tickets");
                })
                .verifyComplete();
    }

    @Test
    void shouldSetReservationExpiresAtTenMinutesAfterReservedAt() {
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));

        StepVerifier.create(adapter.reserveAndCreateTickets(EVENT_ID, 1, ORDER_ID))
                .assertNext(result -> {
                    var ticket = ((TicketReservationResult.Success) result).reservedTickets().get(0);
                    long diffSeconds = ticket.getReservationExpiresAt().getEpochSecond()
                            - ticket.getReservedAt().getEpochSecond();
                    assertThat(diffSeconds).isEqualTo(600);
                })
                .verifyComplete();
    }
}
