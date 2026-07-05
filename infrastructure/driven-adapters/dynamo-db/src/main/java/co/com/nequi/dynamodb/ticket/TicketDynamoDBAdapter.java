package co.com.nequi.dynamodb.ticket;

import co.com.nequi.model.ticket.TicketReservationResult;
import co.com.nequi.model.ticket.gateways.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Ticket items share the tickets table with Event metadata: pk=eventId, sk=ticketId
 * (Event metadata uses sk="METADATA" in the same partition). Reservation is a single
 * DynamoDB transaction so that either every requested ticket flips AVAILABLE -> RESERVED
 * or none of them do; per-item ConditionExpression rejects tickets already taken.
 * Request construction lives in TicketReservationRequestBuilder and response/error
 * mapping in TicketReservationResultMapper — this class only orchestrates the two.
 */
@Repository
public class TicketDynamoDBAdapter implements TicketRepository {

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(10);

    private final DynamoDbAsyncClient client;
    private final String ticketsTableName;

    public TicketDynamoDBAdapter(DynamoDbAsyncClient client,
                                  @Value("${adapter.dynamodb.tickets-table-name}") String ticketsTableName) {
        this.client = client;
        this.ticketsTableName = ticketsTableName;
    }

    @Override
    public Mono<TicketReservationResult> reserveTickets(String eventId, List<String> ticketIds, String orderId) {
        Instant reservedAt = Instant.now();
        Instant expiresAt = reservedAt.plus(RESERVATION_TTL);
        TransactWriteItemsRequest request = TicketReservationRequestBuilder.build(
                ticketsTableName, eventId, ticketIds, orderId, reservedAt, expiresAt);

        return Mono.fromFuture(client.transactWriteItems(request))
                .<TicketReservationResult>map(response ->
                        TicketReservationResultMapper.toSuccess(eventId, ticketIds, orderId, reservedAt, expiresAt))
                .onErrorResume(TransactionCanceledException.class,
                        ex -> Mono.just(TicketReservationResultMapper.toFailure(ticketIds, ex)));
    }
}
