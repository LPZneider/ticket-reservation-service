package co.com.nequi.dynamodb.ticket;

import co.com.nequi.model.ticket.Ticket;
import co.com.nequi.model.ticket.TicketReservationResult;
import co.com.nequi.model.ticket.TicketStatus;
import co.com.nequi.model.ticket.gateways.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ticket items share the tickets table with Event metadata: pk=eventId, sk=ticketId
 * (Event metadata uses sk="METADATA" in the same partition). Reservation is a single
 * DynamoDB transaction so that either every requested ticket flips AVAILABLE -> RESERVED
 * or none of them do; per-item ConditionExpression rejects tickets already taken.
 */
@Repository
public class TicketDynamoDBAdapter implements TicketRepository {

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(10);
    private static final String CONDITIONAL_CHECK_FAILED = "ConditionalCheckFailed";

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
        TransactWriteItemsRequest request = buildRequest(eventId, ticketIds, orderId, reservedAt, expiresAt);

        return Mono.fromFuture(client.transactWriteItems(request))
                .<TicketReservationResult>map(response ->
                        new TicketReservationResult.Success(toReservedTickets(eventId, ticketIds, orderId, reservedAt, expiresAt)))
                .onErrorResume(TransactionCanceledException.class,
                        ex -> Mono.just(new TicketReservationResult.Failure(unavailableTicketIds(ticketIds, ex))));
    }

    private TransactWriteItemsRequest buildRequest(String eventId, List<String> ticketIds, String orderId,
                                                    Instant reservedAt, Instant expiresAt) {
        List<TransactWriteItem> items = ticketIds.stream()
                .map(ticketId -> TransactWriteItem.builder()
                        .update(Update.builder()
                                .tableName(ticketsTableName)
                                .key(Map.of(
                                        "pk", AttributeValue.fromS(eventId),
                                        "sk", AttributeValue.fromS(ticketId)))
                                .updateExpression("SET #status = :reserved, orderId = :orderId, "
                                        + "version = if_not_exists(version, :zero) + :one, "
                                        + "reservedAt = :reservedAt, reservationExpiresAt = :expiresAt")
                                .conditionExpression("#status = :available")
                                .expressionAttributeNames(Map.of("#status", "status"))
                                .expressionAttributeValues(Map.of(
                                        ":reserved", AttributeValue.fromS(TicketStatus.RESERVED.name()),
                                        ":available", AttributeValue.fromS(TicketStatus.AVAILABLE.name()),
                                        ":orderId", AttributeValue.fromS(orderId),
                                        ":zero", AttributeValue.fromN("0"),
                                        ":one", AttributeValue.fromN("1"),
                                        ":reservedAt", AttributeValue.fromS(reservedAt.toString()),
                                        ":expiresAt", AttributeValue.fromN(String.valueOf(expiresAt.getEpochSecond()))))
                                .build())
                        .build())
                .toList();
        return TransactWriteItemsRequest.builder().transactItems(items).build();
    }

    private List<String> unavailableTicketIds(List<String> ticketIds, TransactionCanceledException ex) {
        List<String> unavailable = new ArrayList<>();
        List<CancellationReason> reasons = ex.cancellationReasons();
        for (int i = 0; i < reasons.size(); i++) {
            if (CONDITIONAL_CHECK_FAILED.equals(reasons.get(i).code())) {
                unavailable.add(ticketIds.get(i));
            }
        }
        return unavailable;
    }

    private List<Ticket> toReservedTickets(String eventId, List<String> ticketIds, String orderId,
                                            Instant reservedAt, Instant expiresAt) {
        return ticketIds.stream()
                .map(ticketId -> Ticket.builder()
                        .ticketId(ticketId)
                        .eventId(eventId)
                        .status(TicketStatus.RESERVED)
                        .orderId(orderId)
                        .reservedAt(reservedAt)
                        .reservationExpiresAt(expiresAt)
                        .build())
                .toList();
    }
}
