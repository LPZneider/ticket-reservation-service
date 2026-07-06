package co.com.nequi.api.validator;

import co.com.nequi.api.dto.request.CreateEventRequest;
import co.com.nequi.api.dto.request.ReserveTicketRequest;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidatorTest {

    @Test
    void createEventPassesWhenAllFieldsValid() {
        StepVerifier.create(RequestValidator.validate(
                        new CreateEventRequest("Concert", Instant.now(), "Arena", 100)))
                .expectNextMatches(Collection::isEmpty)
                .verifyComplete();
    }

    @Test
    void createEventFailsWhenNameAndVenueBlankAndDateNull() {
        StepVerifier.create(RequestValidator.validate(
                        new CreateEventRequest("", null, "", 100)))
                .expectNextMatches(errors -> errors.size() == 3)
                .verifyComplete();
    }

    @Test
    void reserveTicketPassesWhenQuantityPositive() {
        StepVerifier.create(RequestValidator.validate(
                        new ReserveTicketRequest("event-1", 3, "user-1")))
                .expectNextMatches(Collection::isEmpty)
                .verifyComplete();
    }

    @Test
    void reserveTicketFailsWhenQuantityIsZero() {
        StepVerifier.create(RequestValidator.validate(
                        new ReserveTicketRequest("event-1", 0, "user-1")))
                .expectNextMatches(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getMessage()).contains("quantity");
                    return true;
                })
                .verifyComplete();
    }

    @Test
    void reserveTicketFailsWhenQuantityIsNegative() {
        StepVerifier.create(RequestValidator.validate(
                        new ReserveTicketRequest("event-1", -1, "user-1")))
                .expectNextMatches(errors -> errors.size() == 1)
                .verifyComplete();
    }

    @Test
    void reserveTicketFailsWhenEventIdAndUserIdMissing() {
        StepVerifier.create(RequestValidator.validate(
                        new ReserveTicketRequest(null, 2, null)))
                .expectNextMatches(errors -> errors.size() == 2)
                .verifyComplete();
    }

    @Test
    void reserveTicketFailsWhenAllFieldsInvalid() {
        StepVerifier.create(RequestValidator.validate(
                        new ReserveTicketRequest(null, 0, null)))
                .expectNextMatches(errors -> errors.size() == 3)
                .verifyComplete();
    }
}
