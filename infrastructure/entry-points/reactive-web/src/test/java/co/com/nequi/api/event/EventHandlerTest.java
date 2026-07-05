package co.com.nequi.api.event;

import co.com.nequi.api.RouterRest;
import co.com.nequi.api.purchase.PurchaseHandler;
import co.com.nequi.model.event.Event;
import co.com.nequi.model.exception.InvalidEventCapacityException;
import co.com.nequi.usecase.event.CreateEventUseCase;
import co.com.nequi.usecase.reservation.ReserveTicketUseCase;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {RouterRest.class, EventHandler.class, PurchaseHandler.class})
@Import(EventHandlerTest.MeterRegistryTestConfig.class)
@WebFluxTest
class EventHandlerTest {

    @TestConfiguration
    static class MeterRegistryTestConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CreateEventUseCase createEventUseCase;

    @MockitoBean
    private ReserveTicketUseCase reserveTicketUseCase;

    @Test
    void shouldReturn201WhenEventIsCreated() {
        Instant date = Instant.parse("2026-08-01T20:00:00Z");
        Event event = Event.builder()
                .eventId("event-1")
                .name("Concert")
                .date(date)
                .venue("Main Arena")
                .totalCapacity(100)
                .build();
        when(createEventUseCase.create(anyString(), any(Instant.class), anyString(), anyInt()))
                .thenReturn(Mono.just(event));

        webTestClient.post()
                .uri("/api/v1/events")
                .header("messageId", "msg-1")
                .header("region", "C001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Concert","date":"2026-08-01T20:00:00Z","venue":"Main Arena","totalCapacity":100}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.code").isEqualTo(201)
                .jsonPath("$.messageId").isEqualTo("msg-1")
                .jsonPath("$.data.eventId").isEqualTo("event-1")
                .jsonPath("$.data.totalCapacity").isEqualTo(100);
    }

    @Test
    void shouldReturn400WhenHeadersAreMissing() {
        webTestClient.post()
                .uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Concert","date":"2026-08-01T20:00:00Z","venue":"Main Arena","totalCapacity":100}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(2);
    }

    @Test
    void shouldReturn400WhenCapacityIsInvalid() {
        when(createEventUseCase.create(anyString(), any(Instant.class), anyString(), anyInt()))
                .thenReturn(Mono.error(new InvalidEventCapacityException(0)));

        webTestClient.post()
                .uri("/api/v1/events")
                .header("messageId", "msg-1")
                .header("region", "C001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Concert","date":"2026-08-01T20:00:00Z","venue":"Main Arena","totalCapacity":0}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400WhenNameIsBlank() {
        webTestClient.post()
                .uri("/api/v1/events")
                .header("messageId", "msg-1")
                .header("region", "C001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"","date":"2026-08-01T20:00:00Z","venue":"Main Arena","totalCapacity":100}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @ExtendWith(MockitoExtension.class)
    static class FallbackTest {

        @Mock
        private CreateEventUseCase createEventUseCase;

        private EventHandler handler;

        @BeforeEach
        void setUp() {
            handler = new EventHandler(createEventUseCase, new SimpleMeterRegistry());
        }

        private ServerRequest buildRequest(String messageId) {
            MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest.post("/api/v1/events")
                    .header("region", "C001");
            if (messageId != null) {
                builder.header("messageId", messageId);
            }
            MockServerWebExchange exchange = MockServerWebExchange.from(builder.build());
            return ServerRequest.create(exchange, Collections.emptyList());
        }

        @Test
        void fallbackWithGenericExceptionReturns500() {
            ServerRequest request = buildRequest("msg-1");
            StepVerifier.create(handler.fallback(request, new RuntimeException("boom")))
                    .expectNextMatches(response -> response.statusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
                    .verifyComplete();
        }

        @Test
        void fallbackWithCallNotPermittedExceptionReturns503() {
            CircuitBreaker cb = CircuitBreakerRegistry.ofDefaults().circuitBreaker("testCb");
            cb.transitionToOpenState();
            CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(cb);

            ServerRequest request = buildRequest("msg-1");
            StepVerifier.create(handler.fallback(request, ex))
                    .expectNextMatches(response -> response.statusCode() == HttpStatus.SERVICE_UNAVAILABLE)
                    .verifyComplete();
        }
    }
}
