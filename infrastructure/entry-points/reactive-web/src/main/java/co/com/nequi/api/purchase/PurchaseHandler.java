package co.com.nequi.api.purchase;

import co.com.nequi.api.dto.request.ReserveTicketRequest;
import co.com.nequi.api.dto.response.ApiResponse;
import co.com.nequi.api.dto.response.PurchaseAcceptedResponse;
import co.com.nequi.api.dto.response.StatusResponseBodyApi;
import co.com.nequi.api.util.enums.TechnicalMessage;
import co.com.nequi.api.validator.HeaderValidator;
import co.com.nequi.api.validator.RequestValidator;
import co.com.nequi.model.reservation.ReservationResult;
import co.com.nequi.usecase.reservation.ReserveTicketUseCase;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;

import static co.com.nequi.api.util.constant.HandlerConstantsApi.HEADER_MESSAGE_ID;
import static co.com.nequi.api.util.constant.HandlerConstantsApi.HEADER_REGION;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseHandler {

    private static final String CIRCUIT_BREAKER_NAME = "reserveTickets";
    private static final String FALLBACK_METHOD = "fallback";
    private static final String TAG_STATUS = "status";

    private final ReserveTicketUseCase reserveTicketUseCase;
    private final MeterRegistry meterRegistry;

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = FALLBACK_METHOD)
    public Mono<ServerResponse> handle(ServerRequest request) {
        String messageId = request.headers().firstHeader(HEADER_MESSAGE_ID);
        String region = request.headers().firstHeader(HEADER_REGION);

        return HeaderValidator.headers(messageId, region)
                .filter(errors -> !errors.isEmpty())
                .flatMap(errors -> {
                    log.warn("[PURCHASES] Header validation failed | messageId={}, errors={}", messageId, errors);
                    return buildBadRequest(messageId, errors);
                })
                .switchIfEmpty(Mono.defer(() ->
                        request.bodyToMono(ReserveTicketRequest.class)
                                .flatMap(body -> RequestValidator.validate(body)
                                        .filter(errors -> !errors.isEmpty())
                                        .flatMap(errors -> {
                                            log.warn("[PURCHASES] Body validation failed | messageId={}, errors={}", messageId, errors);
                                            return buildBadRequest(messageId, errors);
                                        })
                                        .switchIfEmpty(Mono.defer(() -> processValidRequest(body, messageId, region))))))
                .onErrorResume(ex -> {
                    log.error("[PURCHASES] Unexpected error | messageId={}, message={}", messageId, ex.getMessage(), ex);
                    return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .bodyValue(errorResponse(messageId, HttpStatus.INTERNAL_SERVER_ERROR, TechnicalMessage.ERROR_INTERNAL_SERVER));
                });
    }

    public Mono<ServerResponse> fallback(ServerRequest request, Exception exception) {
        String messageId = request.headers().firstHeader(HEADER_MESSAGE_ID);
        log.error("[PURCHASES] Fallback triggered | messageId={}, exception={}, message={}",
                messageId, exception.getClass().getSimpleName(), exception.getMessage(), exception);
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue(errorResponse(messageId, HttpStatus.INTERNAL_SERVER_ERROR, TechnicalMessage.ERROR_INTERNAL_SERVER));
    }

    public Mono<ServerResponse> fallback(ServerRequest request, CallNotPermittedException exception) {
        String messageId = request.headers().firstHeader(HEADER_MESSAGE_ID);
        log.error("[PURCHASES] Circuit breaker OPEN | messageId={}, message={}", messageId, exception.getMessage());
        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyValue(errorResponse(messageId, HttpStatus.SERVICE_UNAVAILABLE, TechnicalMessage.ERROR_SERVICE_UNAVAILABLE));
    }

    private Mono<ServerResponse> processValidRequest(ReserveTicketRequest body, String messageId, String region) {
        Timer.Sample sample = Timer.start(meterRegistry);
        return reserveTicketUseCase.reserve(body.eventId(), body.quantity(), body.userId())
                .doOnSuccess(result -> {
                    String status = result instanceof ReservationResult.Success ? "success" : "conflict";
                    sample.stop(Timer.builder("purchases.reserve.duration").tag(TAG_STATUS, status).register(meterRegistry));
                    meterRegistry.counter("purchases.reserved", TAG_STATUS, status).increment();
                })
                .doOnError(e -> {
                    sample.stop(Timer.builder("purchases.reserve.duration").tag(TAG_STATUS, "error").register(meterRegistry));
                    meterRegistry.counter("purchases.reserved", TAG_STATUS, "error").increment();
                })
                .flatMap(result -> toServerResponse(result, messageId, region));
    }

    private Mono<ServerResponse> toServerResponse(ReservationResult result, String messageId, String region) {
        return switch (result) {
            case ReservationResult.Success success -> {
                logPublishOutcome(success);
                yield ServerResponse.status(HttpStatus.ACCEPTED).bodyValue(
                        ApiResponse.builder()
                                .code(HttpStatus.ACCEPTED.value())
                                .description(HttpStatus.ACCEPTED.getReasonPhrase())
                                .messageId(messageId)
                                .region(region)
                                .data(new PurchaseAcceptedResponse(
                                        success.order().getOrderId(),
                                        success.order().getOrderStatus().name()))
                                .build());
            }
            case ReservationResult.Failure failure -> ServerResponse.status(HttpStatus.CONFLICT).bodyValue(
                    ApiResponse.builder()
                            .code(HttpStatus.CONFLICT.value())
                            .description(HttpStatus.CONFLICT.getReasonPhrase())
                            .messageId(messageId)
                            .errors(List.of(StatusResponseBodyApi.builder()
                                    .code(TechnicalMessage.TICKETS_UNAVAILABLE.getCode())
                                    .message(failure.reason())
                                    .system(TechnicalMessage.TICKETS_UNAVAILABLE.getSystem())
                                    .build()))
                            .build());
        };
    }

    private void logPublishOutcome(ReservationResult.Success success) {
        String orderId = success.order().getOrderId();
        boolean purchaseOk = success.purchaseRequestPublished();
        boolean expiryOk = success.reservationExpiryPublished();
        if (!purchaseOk && !expiryOk) {
            log.error("[PURCHASES] CRITICAL: both purchase-requests and reservation-expiry publishes failed after "
                    + "retries | orderId={} — order stuck in PENDING_CONFIRMATION with no automatic resolution path, "
                    + "manual intervention required", orderId);
        } else if (!purchaseOk) {
            log.warn("[PURCHASES] purchase-requests publish failed after retries, relying on reservation-expiry "
                    + "as fail-safe | orderId={}", orderId);
        } else if (!expiryOk) {
            log.warn("[PURCHASES] reservation-expiry publish failed after retries | orderId={}", orderId);
        }
    }

    private Mono<ServerResponse> buildBadRequest(String messageId, List<StatusResponseBodyApi> errors) {
        return ServerResponse.badRequest().bodyValue(
                ApiResponse.builder()
                        .code(HttpStatus.BAD_REQUEST.value())
                        .description(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .messageId(messageId)
                        .errors(errors)
                        .build());
    }

    private static ApiResponse errorResponse(String messageId, HttpStatus status, TechnicalMessage tm) {
        return ApiResponse.builder()
                .code(status.value())
                .description(status.getReasonPhrase())
                .messageId(messageId)
                .errors(List.of(StatusResponseBodyApi.builder()
                        .code(tm.getCode())
                        .message(tm.getMessage())
                        .system(tm.getSystem())
                        .build()))
                .build();
    }
}
