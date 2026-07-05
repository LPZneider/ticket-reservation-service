package co.com.nequi.api.dto.response;

import co.com.nequi.api.util.constant.SchemaConstantsApi;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Value
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder(toBuilder = true)
@Schema(description = SchemaConstantsApi.DETAIL_RESPONSE_BODY_TITLE)
public class StatusResponseBodyApi {

    @JsonProperty("code")
    @JsonInclude(NON_NULL)
    @Schema(description = SchemaConstantsApi.CODE_DESCRIPTION)
    String code;

    @JsonProperty("message")
    @JsonInclude(NON_NULL)
    @Schema(description = SchemaConstantsApi.MESSAGE_DESCRIPTION)
    String message;

    @JsonProperty("system")
    @JsonInclude(NON_NULL)
    @Schema(description = SchemaConstantsApi.SYSTEM_DESCRIPTION)
    String system;
}
