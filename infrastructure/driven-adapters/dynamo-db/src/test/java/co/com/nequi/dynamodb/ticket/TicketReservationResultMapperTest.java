package co.com.nequi.dynamodb.ticket;

import co.com.nequi.model.ticket.TicketReservationResult;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketReservationResultMapperTest {

    @Test
    void shouldBuildSuccessWithOneReservedTicketPerId() {
        Instant reservedAt = Instant.parse("2026-07-01T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-07-01T10:10:00Z");

        TicketReservationResult result = TicketReservationResultMapper.toSuccess(
                "event-1", List.of("t1", "t2"), "order-1", reservedAt, expiresAt);

        assertThat(result).isInstanceOf(TicketReservationResult.Success.class);
        var success = (TicketReservationResult.Success) result;
        assertThat(success.reservedTickets()).hasSize(2);
        assertThat(success.reservedTickets().get(1).getTicketId()).isEqualTo("t2");
        assertThat(success.reservedTickets().get(1).getReservationExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void shouldOnlyReportTicketsThatFailedTheirOwnCondition() {
        TransactionCanceledException exception = TransactionCanceledException.builder()
                .cancellationReasons(
                        CancellationReason.builder().code("None").build(),
                        CancellationReason.builder().code("ConditionalCheckFailed").build(),
                        CancellationReason.builder().code("None").build())
                .build();

        TicketReservationResult result = TicketReservationResultMapper.toFailure(
                List.of("t1", "t2", "t3"), exception);

        assertThat(result).isInstanceOf(TicketReservationResult.Failure.class);
        assertThat(((TicketReservationResult.Failure) result).unavailableTicketIds()).containsExactly("t2");
    }
}
