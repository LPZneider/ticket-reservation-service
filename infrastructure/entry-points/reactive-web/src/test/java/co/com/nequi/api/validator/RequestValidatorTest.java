package co.com.nequi.api.validator;

import co.com.nequi.api.dto.request.CreateEventRequest;
import co.com.nequi.api.dto.request.ReserveTicketRequest;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidatorTest {

    @Test
    void createEventPassesWhenValid() {
        CreateEventRequest request = new CreateEventRequest("Concert", Instant.now(), "Main Arena", 100);

        StepVerifier.create(RequestValidator.validate(request))
                .expectNextMatches(Collection::isEmpty)
                .verifyComplete();
    }

    @Test
    void createEventFailsWhenAllFieldsMissing() {
        CreateEventRequest request = new CreateEventRequest("", null, "", 100);

        StepVerifier.create(RequestValidator.validate(request))
                .expectNextMatches(errors -> {
                    assertThat(errors).hasSize(3);
                    return true;
                })
                .verifyComplete();
    }

    @Test
    void reserveTicketPassesWhenValid() {
        ReserveTicketRequest request = new ReserveTicketRequest("event-1", List.of("t1"), "user-1");

        StepVerifier.create(RequestValidator.validate(request))
                .expectNextMatches(Collection::isEmpty)
                .verifyComplete();
    }

    @Test
    void reserveTicketFailsWhenTicketIdsEmpty() {
        ReserveTicketRequest request = new ReserveTicketRequest("event-1", List.of(), "user-1");

        StepVerifier.create(RequestValidator.validate(request))
                .expectNextMatches(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getMessage()).contains("ticketIds");
                    return true;
                })
                .verifyComplete();
    }

    @Test
    void reserveTicketFailsWhenAllFieldsMissing() {
        ReserveTicketRequest request = new ReserveTicketRequest(null, null, null);

        StepVerifier.create(RequestValidator.validate(request))
                .expectNextMatches(errors -> errors.size() == 3)
                .verifyComplete();
    }
}
