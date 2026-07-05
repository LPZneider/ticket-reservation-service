package co.com.nequi.sqs.sender.expiry;

import co.com.nequi.model.reservation.gateways.ReservationExpiryPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Duration;
import java.util.List;

@Log4j2
@Service
public class ReservationExpirySQSAdapter implements ReservationExpiryPublisher {

    private static final int RESERVATION_EXPIRY_DELAY_SECONDS = 600;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_MIN_BACKOFF = Duration.ofMillis(200);

    private final SqsAsyncClient client;
    private final ObjectMapper objectMapper;
    private final String queueUrl;

    public ReservationExpirySQSAdapter(SqsAsyncClient client,
                                        ObjectMapper objectMapper,
                                        @Value("${adapter.sqs.expiry.queue-url}") String queueUrl) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
    }

    @Override
    public Mono<Void> publish(String orderId, List<String> ticketIds) {
        return Mono.fromCallable(() -> toMessageBody(orderId, ticketIds))
                .flatMap(body -> Mono.fromFuture(() -> client.sendMessage(buildRequest(body)))
                        .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_MIN_BACKOFF)))
                .doOnError(ex -> log.error("[SQS] reservation-expiry publish failed after retries | orderId={}, message={}",
                        orderId, ex.getMessage(), ex))
                .then();
    }

    private String toMessageBody(String orderId, List<String> ticketIds) throws Exception {
        return objectMapper.writeValueAsString(new ReservationExpiryMessage(orderId, ticketIds));
    }

    private SendMessageRequest buildRequest(String body) {
        return SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .delaySeconds(RESERVATION_EXPIRY_DELAY_SECONDS)
                .build();
    }
}
