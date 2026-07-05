package co.com.nequi.model.purchase.gateways;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

public interface PurchaseRequestPublisher {

    Mono<Void> publish(String orderId, String eventId, List<String> ticketIds, String userId, Instant requestedAt);
}
