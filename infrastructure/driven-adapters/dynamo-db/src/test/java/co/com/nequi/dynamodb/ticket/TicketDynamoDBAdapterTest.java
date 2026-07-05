package co.com.nequi.dynamodb.ticket;

import co.com.nequi.model.ticket.TicketReservationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.List;
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

    private static final String EVENT_ID = "event-1";
    private static final List<String> TICKET_IDS = List.of("t1", "t2");
    private static final String ORDER_ID = "order-1";

    @BeforeEach
    void setUp() {
        adapter = new TicketDynamoDBAdapter(client, "tickets");
    }

    @Test
    void shouldReturnSuccessWithReservedTicketsWhenTransactionSucceeds() {
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));

        StepVerifier.create(adapter.reserveTickets(EVENT_ID, TICKET_IDS, ORDER_ID))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(TicketReservationResult.Success.class);
                    var success = (TicketReservationResult.Success) result;
                    assertThat(success.reservedTickets()).hasSize(2);
                    assertThat(success.reservedTickets().get(0).getOrderId()).isEqualTo(ORDER_ID);
                    assertThat(success.reservedTickets().get(0).getEventId()).isEqualTo(EVENT_ID);
                })
                .verifyComplete();

        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(client).transactWriteItems(captor.capture());
        assertThat(captor.getValue().transactItems()).hasSize(2);
        assertThat(captor.getValue().transactItems().get(0).update().tableName()).isEqualTo("tickets");
    }

    @Test
    void shouldReturnFailureWithUnavailableTicketIdsWhenConditionFails() {
        TransactionCanceledException exception = TransactionCanceledException.builder()
                .cancellationReasons(
                        CancellationReason.builder().code("None").build(),
                        CancellationReason.builder().code("ConditionalCheckFailed").build())
                .build();
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(exception));

        StepVerifier.create(adapter.reserveTickets(EVENT_ID, TICKET_IDS, ORDER_ID))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(TicketReservationResult.Failure.class);
                    var failure = (TicketReservationResult.Failure) result;
                    assertThat(failure.unavailableTicketIds()).containsExactly("t2");
                })
                .verifyComplete();
    }
}
