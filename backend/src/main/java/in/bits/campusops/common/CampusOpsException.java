package in.bits.campusops.common;

import org.springframework.http.HttpStatus;

/**
 * Root of every business exception in CampusOps. Each subclass knows its own
 * HTTP status and machine-readable code, so the global handler stays generic.
 */
public abstract class CampusOpsException extends RuntimeException {

    protected CampusOpsException(String message) {
        super(message);
    }

    protected CampusOpsException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract HttpStatus status();

    public abstract String code();
}
