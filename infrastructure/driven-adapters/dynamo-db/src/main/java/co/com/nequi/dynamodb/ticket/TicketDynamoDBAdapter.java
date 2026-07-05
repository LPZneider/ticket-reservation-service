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
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Reservation is a single TransactWriteItems:
 *   - 1 Update on the Event item: availableCount -= quantity (condition: availableCount >= quantity)
 *   - N Put items for the new RESERVED tickets (pk=eventId, sk=ticketId)
 * Either everything commits or nothing does — no partial state possible.
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
    public Mono<TicketReservationResult> reserveAndCreateTickets(String eventId, int quantity, String orderId) {
        Instant reservedAt = Instant.now();
        Instant expiresAt = reservedAt.plus(RESERVATION_TTL);

        List<Ticket> tickets = IntStream.rangeClosed(1, quantity)
                .mapToObj(i -> Ticket.builder()
                        .ticketId(orderId + "-" + i)
                        .eventId(eventId)
                        .status(TicketStatus.RESERVED)
                        .orderId(orderId)
                        .version(0)
                        .reservedAt(reservedAt)
                        .reservationExpiresAt(expiresAt)
                        .build())
                .toList();

        List<TransactWriteItem> items = new ArrayList<>();

        // 1. Decrement availableCount on Event — condition: availableCount >= quantity
        items.add(TransactWriteItem.builder()
                .update(Update.builder()
                        .tableName(ticketsTableName)
                        .key(Map.of(
                                "pk", AttributeValue.fromS(eventId),
                                "sk", AttributeValue.fromS("METADATA")))
                        .updateExpression("SET availableCount = availableCount - :qty")
                        .conditionExpression("availableCount >= :qty")
                        .expressionAttributeValues(Map.of(
                                ":qty", AttributeValue.fromN(String.valueOf(quantity))))
                        .build())
                .build());

        // 2. Put each new RESERVED ticket
        for (Ticket ticket : tickets) {
            items.add(TransactWriteItem.builder()
                    .put(Put.builder()
                            .tableName(ticketsTableName)
                            .item(Map.of(
                                    "pk",                    AttributeValue.fromS(eventId),
                                    "sk",                    AttributeValue.fromS(ticket.getTicketId()),
                                    "ticketId",              AttributeValue.fromS(ticket.getTicketId()),
                                    "eventId",               AttributeValue.fromS(eventId),
                                    "status",                AttributeValue.fromS(TicketStatus.RESERVED.name()),
                                    "orderId",               AttributeValue.fromS(orderId),
                                    "version",               AttributeValue.fromN("0"),
                                    "reservedAt",            AttributeValue.fromS(reservedAt.toString()),
                                    "reservationExpiresAt",  AttributeValue.fromN(String.valueOf(expiresAt.getEpochSecond()))))
                            .build())
                    .build());
        }

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(items)
                .build();

        return Mono.fromFuture(client.transactWriteItems(request))
                .<TicketReservationResult>map(r -> new TicketReservationResult.Success(tickets))
                .onErrorResume(TransactionCanceledException.class,
                        ex -> Mono.just(new TicketReservationResult.Failure("Not enough available tickets for event " + eventId)));
    }
}
