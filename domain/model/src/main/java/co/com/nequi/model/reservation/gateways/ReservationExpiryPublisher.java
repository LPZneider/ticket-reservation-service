package co.com.nequi.model.reservation.gateways;

import reactor.core.publisher.Mono;

import java.util.List;

public interface ReservationExpiryPublisher {

    Mono<Void> publish(String orderId, List<String> ticketIds);
}
