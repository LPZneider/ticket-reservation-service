package co.com.nequi.usecase.event;

import co.com.nequi.model.event.Event;
import co.com.nequi.model.event.gateways.EventRepository;
import co.com.nequi.model.exception.InvalidEventCapacityException;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class CreateEventUseCase {

    private final EventRepository eventRepository;

    public Mono<Event> create(String name, Instant date, String venue, int totalCapacity) {
        if (totalCapacity <= 0) {
            return Mono.error(new InvalidEventCapacityException(totalCapacity));
        }
        Event event = Event.builder()
                .eventId(UUID.randomUUID().toString())
                .name(name)
                .date(date)
                .venue(venue)
                .totalCapacity(totalCapacity)
                .build();
        return eventRepository.save(event);
    }
}
