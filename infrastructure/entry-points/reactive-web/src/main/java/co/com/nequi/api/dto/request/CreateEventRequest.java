package co.com.nequi.api.dto.request;

import java.time.Instant;

public record CreateEventRequest(
        String name,
        Instant date,
        String venue,
        int totalCapacity
) {
}
