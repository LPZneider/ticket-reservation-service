package co.com.nequi.api.dto.response;

import co.com.nequi.api.util.constant.SchemaConstantsApi;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Value
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder(toBuilder = true)
@Schema(description = SchemaConstantsApi.DETAIL_RESPONSE_BODY_TITLE)
public class ApiResponse {

    @JsonProperty("messageId")
    @JsonInclude(NON_NULL)
    String messageId;

    @JsonProperty("region")
    @JsonInclude(NON_NULL)
    String region;

    @JsonProperty("code")
    @JsonInclude(NON_NULL)
    Integer code;

    @JsonProperty("description")
    @JsonInclude(NON_NULL)
    String description;

    @JsonProperty("errors")
    @JsonInclude(NON_NULL)
    List<StatusResponseBodyApi> errors;

    @JsonProperty("data")
    @JsonInclude(NON_NULL)
    Object data;
}
