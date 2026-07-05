package com.nequi.validator;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

@UtilityClass
public class ValidatorHelper {

    public static Mono<Boolean> isValidNotBlank(String field) {
        return Mono.defer(() -> Mono.just(StringUtils.isNotBlank(field)));
    }

    public static Mono<Boolean> isValidNotNull(Object object) {
        return Mono.defer(() -> Mono.just(Objects.nonNull(object)));
    }

    public static Mono<Boolean> isValidNotEmpty(Map<?, ?> map) {
        return Mono.defer(() -> Mono.just(Objects.nonNull(map) && !map.isEmpty()));
    }
}
