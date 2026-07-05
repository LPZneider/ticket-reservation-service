package co.com.nequi.sqs.sender.purchase;

import co.com.nequi.model.purchase.gateways.PurchaseRequestPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Log4j2
@Service
public class PurchaseRequestSQSAdapter implements PurchaseRequestPublisher {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_MIN_BACKOFF = Duration.ofMillis(200);

    private final SqsAsyncClient client;
    private final ObjectMapper objectMapper;
    private final String queueUrl;

    public PurchaseRequestSQSAdapter(SqsAsyncClient client,
                                      ObjectMapper objectMapper,
                                      @Value("${adapter.sqs.purchase.queue-url}") String queueUrl) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
    }

    @Override
    public Mono<Void> publish(String orderId, String eventId, List<String> ticketIds, String userId, Instant requestedAt) {
        return Mono.fromCallable(() -> toMessageBody(orderId, eventId, ticketIds, userId, requestedAt))
                .flatMap(body -> Mono.fromFuture(() -> client.sendMessage(buildRequest(body)))
                        .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_MIN_BACKOFF)))
                .doOnError(ex -> log.error("[SQS] purchase-requests publish failed after retries | orderId={}, message={}",
                        orderId, ex.getMessage(), ex))
                .then();
    }

    private String toMessageBody(String orderId, String eventId, List<String> ticketIds, String userId, Instant requestedAt) throws Exception {
        return objectMapper.writeValueAsString(
                new PurchaseRequestMessage(orderId, eventId, ticketIds, userId, requestedAt.toString()));
    }

    private SendMessageRequest buildRequest(String body) {
        return SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .build();
    }
}
