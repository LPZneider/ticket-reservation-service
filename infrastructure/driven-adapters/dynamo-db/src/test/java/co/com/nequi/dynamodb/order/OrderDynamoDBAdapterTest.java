package co.com.nequi.dynamodb.order;

import co.com.nequi.model.order.Order;
import co.com.nequi.model.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDynamoDBAdapterTest {

    @Mock
    private DynamoDbEnhancedAsyncClient client;
    @Mock
    private DynamoDbAsyncTable<OrderEntity> table;

    private OrderDynamoDBAdapter adapter;

    @BeforeEach
    void setUp() {
        when(client.table("orders", TableSchema.fromBean(OrderEntity.class))).thenReturn(table);
        adapter = new OrderDynamoDBAdapter(client, "orders");
    }

    @Test
    void shouldAppendNewItemPerOrderStateTransition() {
        Order order = Order.builder()
                .orderId("order-1")
                .eventId("event-1")
                .ticketIds(List.of("t1", "t2"))
                .userId("user-1")
                .orderStatus(OrderStatus.PENDING_CONFIRMATION)
                .createdAt(Instant.parse("2026-07-01T10:00:00Z"))
                .build();
        when(table.putItem(any(OrderEntity.class))).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(adapter.save(order))
                .expectNext(order)
                .verifyComplete();

        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(table).putItem(captor.capture());
        assertThat(captor.getValue().getPk()).isEqualTo("order-1");
        assertThat(captor.getValue().getSk()).isEqualTo("2026-07-01T10:00:00Z");
        assertThat(captor.getValue().getOrderStatus()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(captor.getValue().getTicketIds()).containsExactly("t1", "t2");
    }
}
