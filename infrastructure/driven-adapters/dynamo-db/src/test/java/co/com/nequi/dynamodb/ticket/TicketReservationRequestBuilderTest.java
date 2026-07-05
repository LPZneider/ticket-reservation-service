package co.com.nequi.dynamodb.ticket;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketReservationRequestBuilderTest {

    @Test
    void shouldBuildOneConditionalUpdatePerTicket() {
        Instant reservedAt = Instant.parse("2026-07-01T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-07-01T10:10:00Z");

        TransactWriteItemsRequest request = TicketReservationRequestBuilder.build(
                "tickets", "event-1", List.of("t1", "t2"), "order-1", reservedAt, expiresAt);

        assertThat(request.transactItems()).hasSize(2);
        var firstUpdate = request.transactItems().get(0).update();
        assertThat(firstUpdate.tableName()).isEqualTo("tickets");
        assertThat(firstUpdate.key().get("pk").s()).isEqualTo("event-1");
        assertThat(firstUpdate.key().get("sk").s()).isEqualTo("t1");
        assertThat(firstUpdate.conditionExpression()).contains("#status = :available");
        assertThat(firstUpdate.expressionAttributeValues().get(":expiresAt").n())
                .isEqualTo(String.valueOf(expiresAt.getEpochSecond()));
    }
}
