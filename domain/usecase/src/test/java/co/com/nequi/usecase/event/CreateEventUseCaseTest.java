package co.com.nequi.usecase.event;

import co.com.nequi.model.event.Event;
import co.com.nequi.model.event.gateways.EventRepository;
import co.com.nequi.model.exception.InvalidEventCapacityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateEventUseCaseTest {

    @Mock
    private EventRepository eventRepository;

    private CreateEventUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateEventUseCase(eventRepository);
    }

    @Test
    void shouldCreateAndPersistEventWhenCapacityIsPositive() {
        Instant date = Instant.parse("2026-08-01T20:00:00Z");
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.create("Concert", date, "Main Arena", 100))
                .assertNext(event -> {
                    assertThat(event.getEventId()).isNotBlank();
                    assertThat(event.getName()).isEqualTo("Concert");
                    assertThat(event.getDate()).isEqualTo(date);
                    assertThat(event.getVenue()).isEqualTo("Main Arena");
                    assertThat(event.getTotalCapacity()).isEqualTo(100);
                })
                .verifyComplete();

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalCapacity()).isEqualTo(100);
    }

    @Test
    void shouldFailWithInvalidEventCapacityExceptionWhenCapacityIsZero() {
        StepVerifier.create(useCase.create("Concert", Instant.now(), "Main Arena", 0))
                .expectError(InvalidEventCapacityException.class)
                .verify();

        verify(eventRepository, never()).save(any());
    }

    @Test
    void shouldFailWithInvalidEventCapacityExceptionWhenCapacityIsNegative() {
        StepVerifier.create(useCase.create("Concert", Instant.now(), "Main Arena", -5))
                .expectError(InvalidEventCapacityException.class)
                .verify();

        verify(eventRepository, never()).save(any());
    }
}
