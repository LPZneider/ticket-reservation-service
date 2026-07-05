package co.com.nequi.dynamodb.order;

import co.com.nequi.model.order.Order;
import co.com.nequi.model.order.gateways.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
public class OrderDynamoDBAdapter implements OrderRepository {

    private final DynamoDbAsyncTable<OrderEntity> table;

    public OrderDynamoDBAdapter(DynamoDbEnhancedAsyncClient client,
                                 @Value("${adapter.dynamodb.orders-table-name}") String ordersTableName) {
        this.table = client.table(ordersTableName, TableSchema.fromBean(OrderEntity.class));
    }

    @Override
    public Mono<Order> save(Order order) {
        return Mono.fromFuture(table.putItem(toEntity(order))).thenReturn(order);
    }

    private static OrderEntity toEntity(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.setPk(order.getOrderId());
        entity.setSk(order.getCreatedAt().toString());
        entity.setOrderId(order.getOrderId());
        entity.setEventId(order.getEventId());
        entity.setTicketIds(order.getTicketIds());
        entity.setUserId(order.getUserId());
        entity.setOrderStatus(order.getOrderStatus().name());
        entity.setCreatedAt(order.getCreatedAt().toString());
        return entity;
    }
}
