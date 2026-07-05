package co.com.nequi.api.purchase;

import co.com.nequi.api.RouterRest;
import co.com.nequi.api.event.EventHandler;
import co.com.nequi.model.order.Order;
import co.com.nequi.model.order.OrderStatus;
import co.com.nequi.model.reservation.ReservationResult;
import co.com.nequi.usecase.event.CreateEventUseCase;
import co.com.nequi.usecase.reservation.ReserveTicketUseCase;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {RouterRest.class, EventHandler.class, PurchaseHandler.class})
@Import(PurchaseHandlerTest.MeterRegistryTestConfig.class)
@WebFluxTest
class PurchaseHandlerTest {

    @TestConfiguration
    static class MeterRegistryTestConfig {
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }

    @Autowired private WebTestClient webTestClient;
    @MockitoBean private CreateEventUseCase createEventUseCase;
    @MockitoBean private ReserveTicketUseCase reserveTicketUseCase;

    private static Order pendingOrder() {
        return Order.builder().orderId("order-1").eventId("event-1")
                .ticketIds(List.of("order-1-1", "order-1-2")).userId("user-1")
                .orderStatus(OrderStatus.PENDING_CONFIRMATION)
                .createdAt(Instant.parse("2026-07-01T10:00:00Z")).build();
    }

    @Test
    void shouldReturn202WithOrderIdWhenReservationSucceeds() {
        when(reserveTicketUseCase.reserve(anyString(), anyInt(), anyString()))
                .thenReturn(Mono.just(new ReservationResult.Success(pendingOrder(), true, true)));

        webTestClient.post().uri("/api/v1/purchases")
                .header("messageId", "msg-1").header("region", "C001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"eventId":"event-1","quantity":2,"userId":"user-1"}
                        """)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.code").isEqualTo(202)
                .jsonPath("$.data.orderId").isEqualTo("order-1")
                .jsonPath("$.data.orderStatus").isEqualTo("PENDING_CONFIRMATION");
    }

    @Test
    void shouldReturn202EvenWhenBothPublishesFailed() {
        when(reserveTicketUseCase.reserve(anyString(), anyInt(), anyString()))
                .thenReturn(Mono.just(new ReservationResult.Success(pendingOrder(), false, false)));

        webTestClient.post().uri("/api/v1/purchases")
                .header("messageId", "msg-1").header("region", "C001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"eventId":"event-1","quantity":2,"userId":"user-1"}
                        """)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody().jsonPath("$.data.orderId").isEqualTo("order-1");
    }

    @Test
    void shouldReturn409WhenNotEnoughAvailability() {
        when(reserveTicketUseCase.reserve(anyString(), anyInt(), anyString()))
                .thenReturn(Mono.just(new ReservationResult.Failure("Not enough available tickets for event event-1")));

        webTestClient.post().uri("/api/v1/purchases")
                .header("messageId", "msg-1").header("region", "C001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"eventId":"event-1","quantity":2,"userId":"user-1"}
                        """)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.errors[0].message").value(
                        org.hamcrest.Matchers.containsString("Not enough"));
    }

    @Test
    void shouldReturn400WhenHeadersAreMissing() {
        webTestClient.post().uri("/api/v1/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"eventId":"event-1","quantity":2,"userId":"user-1"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400WhenQuantityIsZero() {
        webTestClient.post().uri("/api/v1/purchases")
                .header("messageId", "msg-1").header("region", "C001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"eventId":"event-1","quantity":0,"userId":"user-1"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400WhenEventIdMissing() {
        webTestClient.post().uri("/api/v1/purchases")
                .header("messageId", "msg-1").header("region", "C001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"quantity":2,"userId":"user-1"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @ExtendWith(MockitoExtension.class)
    static class FallbackTest {

        @Mock private ReserveTicketUseCase reserveTicketUseCase;
        private PurchaseHandler handler;

        @BeforeEach
        void setUp() {
            handler = new PurchaseHandler(reserveTicketUseCase, new SimpleMeterRegistry());
        }

        private ServerRequest buildRequest(String messageId) {
            MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest.post("/api/v1/purchases")
                    .header("region", "C001");
            if (messageId != null) builder.header("messageId", messageId);
            return ServerRequest.create(MockServerWebExchange.from(builder.build()), Collections.emptyList());
        }

        @Test
        void fallbackWithGenericExceptionReturns500() {
            StepVerifier.create(handler.fallback(buildRequest("msg-1"), new RuntimeException("boom")))
                    .expectNextMatches(r -> r.statusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
                    .verifyComplete();
        }

        @Test
        void fallbackWithCallNotPermittedReturns503() {
            CircuitBreaker cb = CircuitBreakerRegistry.ofDefaults().circuitBreaker("cb");
            cb.transitionToOpenState();
            StepVerifier.create(handler.fallback(buildRequest("msg-1"),
                            CallNotPermittedException.createCallNotPermittedException(cb)))
                    .expectNextMatches(r -> r.statusCode() == HttpStatus.SERVICE_UNAVAILABLE)
                    .verifyComplete();
        }
    }
}
