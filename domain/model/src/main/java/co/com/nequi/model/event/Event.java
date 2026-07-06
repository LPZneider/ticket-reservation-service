package co.com.nequi.model.event;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class Event {
    String eventId;
    String name;
    Instant date;
    String venue;
    int totalCapacity;
    int availableCount;
}
