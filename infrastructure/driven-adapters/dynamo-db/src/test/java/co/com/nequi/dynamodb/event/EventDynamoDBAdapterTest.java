package co.com.nequi.dynamodb.event;

import co.com.nequi.model.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventDynamoDBAdapterTest {

    @Mock
    private DynamoDbEnhancedAsyncClient client;
    @Mock
    private DynamoDbAsyncTable<EventEntity> table;

    private EventDynamoDBAdapter adapter;

    @BeforeEach
    void setUp() {
        when(client.table("tickets", TableSchema.fromBean(EventEntity.class))).thenReturn(table);
        adapter = new EventDynamoDBAdapter(client, "tickets");
    }

    @Test
    void shouldSaveEventAsMetadataItem() {
        Event event = Event.builder()
                .eventId("event-1")
                .name("Concert")
                .date(Instant.parse("2026-08-01T20:00:00Z"))
                .venue("Main Arena")
                .totalCapacity(100)
                .build();
        when(table.putItem(any(EventEntity.class))).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(adapter.save(event))
                .expectNext(event)
                .verifyComplete();

        ArgumentCaptor<EventEntity> captor = ArgumentCaptor.forClass(EventEntity.class);
        verify(table).putItem(captor.capture());
        assertThat(captor.getValue().getPk()).isEqualTo("event-1");
        assertThat(captor.getValue().getSk()).isEqualTo(EventEntity.METADATA_SORT_KEY);
    }

    @Test
    void shouldFindEventByIdUsingMetadataSortKey() {
        EventEntity entity = new EventEntity();
        entity.setPk("event-1");
        entity.setSk(EventEntity.METADATA_SORT_KEY);
        entity.setEventId("event-1");
        entity.setName("Concert");
        entity.setDate("2026-08-01T20:00:00Z");
        entity.setVenue("Main Arena");
        entity.setTotalCapacity(100);

        Key expectedKey = Key.builder().partitionValue("event-1").sortValue(EventEntity.METADATA_SORT_KEY).build();
        when(table.getItem(expectedKey)).thenReturn(CompletableFuture.completedFuture(entity));

        StepVerifier.create(adapter.findById("event-1"))
                .assertNext(event -> {
                    assertThat(event.getEventId()).isEqualTo("event-1");
                    assertThat(event.getName()).isEqualTo("Concert");
                    assertThat(event.getTotalCapacity()).isEqualTo(100);
                })
                .verifyComplete();
    }

    @Test
    void shouldCompleteEmptyWhenEventNotFound() {
        Key expectedKey = Key.builder().partitionValue("missing").sortValue(EventEntity.METADATA_SORT_KEY).build();
        when(table.getItem(expectedKey)).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(adapter.findById("missing"))
                .verifyComplete();
    }
}
