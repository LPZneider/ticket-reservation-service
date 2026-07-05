package co.com.nequi.api.dto.response;

import java.time.Instant;

public record EventResponse(
        String eventId,
        String name,
        Instant date,
        String venue,
        int totalCapacity
) {
}
