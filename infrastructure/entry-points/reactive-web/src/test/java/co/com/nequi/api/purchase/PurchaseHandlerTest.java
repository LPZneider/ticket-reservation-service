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
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {RouterRest.class, EventHandler.class, PurchaseHandler.class})
@Import(PurchaseHandlerTest.MeterRegistryTestConfig.class)
@WebFluxTest
class PurchaseHandlerTest {

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
    void shouldReturn202WhenReservationSucceeds() {
        Order order = Order.builder()
                .orderId("order-1")
                .eventId("event-1")
                .ticketIds(List.of("t1", "t2"))
                .userId("user-1")
                .orderStatus(OrderStatus.PENDING_CONFIRMATION)
                .createdAt(Instant.parse("2026-07-01T10:00:00Z"))
                .build();
        when(reserveTicketUseCase.reserve(anyString(), anyList(), anyString()))
                .thenReturn(Mono.just(new ReservationResult.Success(order, true, true)));

        webTestClient.post()
                .uri("/api/v1/purchases")
                .header("messageId", "msg-1")
                .header("region", "C001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"eventId":"event-1","ticketIds":["t1","t2"],"userId":"user-1"}
                        """)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.code").isEqualTo(202)
                .jsonPath("$.data.orderId").isEqualTo("order-1")
                .jsonPath("$.data.orderStatus").isEqualTo("PENDING_CONFIRMATION");
    }

    @Test
    void shouldReturn202EvenWhenBothPublishesFailedAfterRetries() {
        Order order = Order.builder()
                .orderId("order-1")
                .eventId("event-1")
                .ticketIds(List.of("t1", "t2"))
                .userId("user-1")
                .orderStatus(OrderStatus.PENDING_CONFIRMATION)
                .createdAt(Instant.parse("2026-07-01T10:00:00Z"))
                .build();
        when(reserveTicketUseCase.reserve(anyString(), anyList(), anyString()))
                .thenReturn(Mono.just(new ReservationResult.Success(order, false, false)));

        webTestClient.post()
                .uri("/api/v1/purchases")
                .header("messageId", "msg-1")
                .header("region", "C001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"eventId":"event-1","ticketIds":["t1","t2"],"userId":"user-1"}
                        """)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.data.orderId").isEqualTo("order-1");
    }

    @Test
    void shouldReturn409WhenTicketsAreUnavailable() {
        when(reserveTicketUseCase.reserve(anyString(), anyList(), anyString()))
                .thenReturn(Mono.just(new ReservationResult.Failure(List.of("t2"))));

        webTestClient.post()
                .uri("/api/v1/purchases")
                .header("messageId", "msg-1")
                .header("region", "C001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"eventId":"event-1","ticketIds":["t1","t2"],"userId":"user-1"}
                        """)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("t2"));
    }

    @Test
    void shouldReturn400WhenHeadersAreMissing() {
        webTestClient.post()
                .uri("/api/v1/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"eventId":"event-1","ticketIds":["t1","t2"],"userId":"user-1"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400WhenTicketIdsIsEmpty() {
        webTestClient.post()
                .uri("/api/v1/purchases")
                .header("messageId", "msg-1")
                .header("region", "C001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"eventId":"event-1","ticketIds":[],"userId":"user-1"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @ExtendWith(MockitoExtension.class)
    static class FallbackTest {

        @Mock
        private ReserveTicketUseCase reserveTicketUseCase;

        private PurchaseHandler handler;

        @BeforeEach
        void setUp() {
            handler = new PurchaseHandler(reserveTicketUseCase, new SimpleMeterRegistry());
        }

        private ServerRequest buildRequest(String messageId) {
            MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest.post("/api/v1/purchases")
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
