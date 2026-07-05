package co.com.nequi.api.util;

import co.com.nequi.api.dto.response.StatusResponseBodyApi;
import co.com.nequi.api.util.enums.TechnicalMessage;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class ValidationUtilApi {

    public static void validateField(Boolean isValid, List<StatusResponseBodyApi> errors, TechnicalMessage technicalMessage) {
        if (Boolean.FALSE.equals(isValid)) {
            errors.add(buildErrorDetail(technicalMessage));
        }
    }

    private static StatusResponseBodyApi buildErrorDetail(TechnicalMessage technicalMessage) {
        return StatusResponseBodyApi.builder()
                .code(technicalMessage.getCode())
                .message(technicalMessage.getMessage())
                .system(technicalMessage.getSystem())
                .build();
    }
}
