package co.com.nequi.sqs.sender.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adapter.sqs")
public record SqsProperties(
        String region,
        String endpoint,
        QueueProperties purchase,
        QueueProperties expiry
) {

    public record QueueProperties(String queueUrl, String queueName) {
    }
}
