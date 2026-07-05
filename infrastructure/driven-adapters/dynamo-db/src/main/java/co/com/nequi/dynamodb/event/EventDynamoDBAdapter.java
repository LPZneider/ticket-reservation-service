package co.com.nequi.dynamodb.event;

import co.com.nequi.model.event.Event;
import co.com.nequi.model.event.gateways.EventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;

@Repository
public class EventDynamoDBAdapter implements EventRepository {

    private final DynamoDbAsyncTable<EventEntity> table;

    public EventDynamoDBAdapter(DynamoDbEnhancedAsyncClient client,
                                 @Value("${adapter.dynamodb.tickets-table-name}") String ticketsTableName) {
        this.table = client.table(ticketsTableName, TableSchema.fromBean(EventEntity.class));
    }

    @Override
    public Mono<Event> save(Event event) {
        return Mono.fromFuture(table.putItem(toEntity(event))).thenReturn(event);
    }

    @Override
    public Mono<Event> findById(String eventId) {
        Key key = Key.builder().partitionValue(eventId).sortValue(EventEntity.METADATA_SORT_KEY).build();
        return Mono.fromFuture(table.getItem(key)).map(EventDynamoDBAdapter::toModel);
    }

    private static EventEntity toEntity(Event event) {
        EventEntity entity = new EventEntity();
        entity.setPk(event.getEventId());
        entity.setSk(EventEntity.METADATA_SORT_KEY);
        entity.setEventId(event.getEventId());
        entity.setName(event.getName());
        entity.setDate(event.getDate().toString());
        entity.setVenue(event.getVenue());
        entity.setTotalCapacity(event.getTotalCapacity());
        return entity;
    }

    private static Event toModel(EventEntity entity) {
        return Event.builder()
                .eventId(entity.getEventId())
                .name(entity.getName())
                .date(Instant.parse(entity.getDate()))
                .venue(entity.getVenue())
                .totalCapacity(entity.getTotalCapacity())
                .build();
    }
}
