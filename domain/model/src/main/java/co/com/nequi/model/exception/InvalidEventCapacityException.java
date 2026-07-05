package co.com.nequi.model.exception;

public class InvalidEventCapacityException extends RuntimeException {

    public InvalidEventCapacityException(int totalCapacity) {
        super("Event totalCapacity must be positive, got: " + totalCapacity);
    }
}
