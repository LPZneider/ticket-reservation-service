package co.com.nequi.api.dto.request;

import java.util.List;

public record ReserveTicketRequest(
        String eventId,
        List<String> ticketIds,
        String userId
) {
}
