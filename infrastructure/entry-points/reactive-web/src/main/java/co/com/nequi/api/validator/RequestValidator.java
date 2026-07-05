package co.com.nequi.api.validator;

import co.com.nequi.api.dto.request.CreateEventRequest;
import co.com.nequi.api.dto.request.ReserveTicketRequest;
import co.com.nequi.api.dto.response.StatusResponseBodyApi;
import co.com.nequi.api.util.enums.TechnicalMessage;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class RequestValidator {

    public static Mono<List<StatusResponseBodyApi>> validate(CreateEventRequest request) {
        return Mono.fromCallable(() -> {
            List<StatusResponseBodyApi> errors = new ArrayList<>();
            if (request.name() == null || request.name().isBlank()) {
                errors.add(buildError(TechnicalMessage.ERROR_BAD_REQUEST, "name is required"));
            }
            if (request.venue() == null || request.venue().isBlank()) {
                errors.add(buildError(TechnicalMessage.ERROR_BAD_REQUEST, "venue is required"));
            }
            if (request.date() == null) {
                errors.add(buildError(TechnicalMessage.ERROR_BAD_REQUEST, "date is required"));
            }
            return errors;
        });
    }

    public static Mono<List<StatusResponseBodyApi>> validate(ReserveTicketRequest request) {
        return Mono.fromCallable(() -> {
            List<StatusResponseBodyApi> errors = new ArrayList<>();
            if (request.eventId() == null || request.eventId().isBlank()) {
                errors.add(buildError(TechnicalMessage.ERROR_BAD_REQUEST, "eventId is required"));
            }
            if (request.ticketIds() == null || request.ticketIds().isEmpty()) {
                errors.add(buildError(TechnicalMessage.ERROR_BAD_REQUEST, "ticketIds must not be empty"));
            }
            if (request.userId() == null || request.userId().isBlank()) {
                errors.add(buildError(TechnicalMessage.ERROR_BAD_REQUEST, "userId is required"));
            }
            return errors;
        });
    }

    private static StatusResponseBodyApi buildError(TechnicalMessage tm, String detail) {
        return StatusResponseBodyApi.builder()
                .code(tm.getCode())
                .message(detail)
                .system(tm.getSystem())
                .build();
    }
}
