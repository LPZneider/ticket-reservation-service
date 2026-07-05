package co.com.nequi.api.util.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TechnicalMessage {

    SUCCESS(200, "0", "SUCCESS"),
    ERROR_BAD_REQUEST(400, "RES-400", "The request headers or body are invalid"),
    EVENT_INVALID_CAPACITY(400, "RES-401", "totalCapacity must be a positive number"),
    TICKETS_UNAVAILABLE(409, "RES-409", "Some requested tickets are no longer available"),
    ERROR_INTERNAL_SERVER(500, "RES-500", "Unexpected error, please contact support"),
    ERROR_SERVICE_UNAVAILABLE(503, "RES-503", "The service is currently unable to handle the request");

    private static final String SYSTEM = "ticket-reservation-service";

    private final int codeHtp;
    private final String code;
    private final String message;

    public String getSystem() {
        return SYSTEM;
    }
}
