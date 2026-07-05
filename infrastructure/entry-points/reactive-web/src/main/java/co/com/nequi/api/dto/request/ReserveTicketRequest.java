package co.com.nequi.api.dto.request;

public record ReserveTicketRequest(
        String eventId,
        int quantity,
        String userId
) {
}
