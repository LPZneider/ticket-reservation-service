package co.com.nequi.api.validator;

import co.com.nequi.api.dto.response.StatusResponseBodyApi;
import co.com.nequi.api.util.ValidationUtilApi;
import co.com.nequi.api.util.enums.TechnicalMessage;
import com.nequi.validator.ValidatorHelper;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class HeaderValidator {

    public static Mono<List<StatusResponseBodyApi>> headers(String messageId, String region) {
        return Mono.zip(
                ValidatorHelper.isValidNotBlank(messageId),
                ValidatorHelper.isValidNotBlank(region)
        ).map(validations -> {
            List<StatusResponseBodyApi> errors = new ArrayList<>();
            ValidationUtilApi.validateField(validations.getT1(), errors, TechnicalMessage.ERROR_BAD_REQUEST);
            ValidationUtilApi.validateField(validations.getT2(), errors, TechnicalMessage.ERROR_BAD_REQUEST);
            return errors;
        });
    }
}
