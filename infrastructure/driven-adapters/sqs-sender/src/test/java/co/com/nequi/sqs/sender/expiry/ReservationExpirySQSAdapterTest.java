package co.com.nequi.sqs.sender.expiry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationExpirySQSAdapterTest {

    @Mock
    private SqsAsyncClient client;

    private ReservationExpirySQSAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ReservationExpirySQSAdapter(client, new ObjectMapper(),
                "http://localhost:4566/000000000000/reservation-expiry");
    }

    @Test
    void shouldPublishExpiryMessageWithSixHundredSecondDelay() {
        when(client.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("m1").build()));

        StepVerifier.create(adapter.publish("order-1", List.of("t1", "t2")))
                .verifyComplete();

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(client).sendMessage(captor.capture());
        SendMessageRequest request = captor.getValue();
        assertThat(request.queueUrl()).isEqualTo("http://localhost:4566/000000000000/reservation-expiry");
        assertThat(request.delaySeconds()).isEqualTo(600);
        assertThat(request.messageBody()).contains("\"orderId\":\"order-1\"");
    }

    @Test
    void shouldRetryAndEventuallySucceedAfterTransientFailures() {
        AtomicInteger attempts = new AtomicInteger();
        when(client.sendMessage(any(SendMessageRequest.class))).thenAnswer(invocation -> {
            if (attempts.getAndIncrement() < 2) {
                return CompletableFuture.failedFuture(new RuntimeException("SQS throttled"));
            }
            return CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("m1").build());
        });

        StepVerifier.create(adapter.publish("order-1", List.of("t1")))
                .verifyComplete();

        verify(client, times(3)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void shouldPropagateErrorAfterExhaustingRetries() {
        when(client.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("SQS unavailable")));

        StepVerifier.create(adapter.publish("order-1", List.of("t1")))
                .expectError()
                .verify(Duration.ofSeconds(5));

        verify(client, times(4)).sendMessage(any(SendMessageRequest.class));
    }
}
