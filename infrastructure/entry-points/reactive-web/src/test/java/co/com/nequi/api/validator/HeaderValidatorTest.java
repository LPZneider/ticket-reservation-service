package co.com.nequi.api.validator;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Collection;

class HeaderValidatorTest {

    @Test
    void headersValidWhenBothPresent() {
        StepVerifier.create(HeaderValidator.headers("msg-123", "C001"))
                .expectNextMatches(Collection::isEmpty)
                .verifyComplete();
    }

    @Test
    void headersInvalidWhenMessageIdBlank() {
        StepVerifier.create(HeaderValidator.headers("", "C001"))
                .expectNextMatches(errors -> errors.size() == 1)
                .verifyComplete();
    }

    @Test
    void headersInvalidWhenRegionMissing() {
        StepVerifier.create(HeaderValidator.headers("msg-123", null))
                .expectNextMatches(errors -> errors.size() == 1)
                .verifyComplete();
    }

    @Test
    void headersInvalidWhenBothMissing() {
        StepVerifier.create(HeaderValidator.headers(null, null))
                .expectNextMatches(errors -> errors.size() == 2)
                .verifyComplete();
    }
}
