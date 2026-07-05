package co.com.nequi.dynamodb.ticket;

import co.com.nequi.model.ticket.TicketStatus;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Builds the TransactWriteItems request for a reservation attempt: one conditional
 * Update per ticket (pk=eventId, sk=ticketId), AVAILABLE -> RESERVED.
 */
final class TicketReservationRequestBuilder {

    private TicketReservationRequestBuilder() {
    }

    static TransactWriteItemsRequest build(String ticketsTableName, String eventId, List<String> ticketIds,
                                            String orderId, Instant reservedAt, Instant expiresAt) {
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
}
