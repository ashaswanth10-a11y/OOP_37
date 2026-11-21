package in.bits.campusops.common;

import org.springframework.http.HttpStatus;

/** Concrete business exceptions, grouped in one file so the whole catalogue is easy to review. */
public final class Exceptions {

    private Exceptions() {
    }

    public static class ResourceNotFoundException extends CampusOpsException {
        public ResourceNotFoundException(String entity, Object id) {
            super(entity + " not found: " + id);
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.NOT_FOUND;
        }

        @Override
        public String code() {
            return "NOT_FOUND";
        }
    }

    public static class ServiceNotFoundException extends ResourceNotFoundException {
        public ServiceNotFoundException(Object id) {
            super("IT service", id);
        }

        @Override
        public String code() {
            return "SERVICE_NOT_FOUND";
        }
    }

    public static class InvalidLoginException extends CampusOpsException {
        public InvalidLoginException() {
            super("Email or password is incorrect");
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.UNAUTHORIZED;
        }

        @Override
        public String code() {
            return "INVALID_LOGIN";
        }
    }

    public static class UnauthorizedOperationException extends CampusOpsException {
        public UnauthorizedOperationException(String message) {
            super(message);
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.FORBIDDEN;
        }

        @Override
        public String code() {
            return "UNAUTHORIZED_OPERATION";
        }
    }

    public static class InvalidStateTransitionException extends CampusOpsException {
        public InvalidStateTransitionException(String message) {
            super(message);
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String code() {
            return "INVALID_STATE_TRANSITION";
        }
    }

    public static class TechnicianUnavailableException extends CampusOpsException {
        public TechnicianUnavailableException(String message) {
            super(message);
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String code() {
            return "TECHNICIAN_UNAVAILABLE";
        }
    }

    public static class WorkloadExceededException extends CampusOpsException {
        public WorkloadExceededException(String technician, int active, int max) {
            super(technician + " already has " + active + " active incidents (limit " + max + ")");
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String code() {
            return "WORKLOAD_EXCEEDED";
        }
    }

    public static class MissingSlaConfigurationException extends CampusOpsException {
        public MissingSlaConfigurationException(String message) {
            super(message);
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }

        @Override
        public String code() {
            return "MISSING_SLA_CONFIGURATION";
        }
    }

    public static class DuplicateIncidentException extends CampusOpsException {
        private final String existingNumber;

        public DuplicateIncidentException(String existingNumber) {
            super("You already reported this problem as " + existingNumber
                    + ". Add details there, or confirm this is a different problem.");
            this.existingNumber = existingNumber;
        }

        public String existingNumber() {
            return existingNumber;
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String code() {
            return "DUPLICATE_INCIDENT";
        }
    }

    public static class InvalidInputException extends CampusOpsException {
        public InvalidInputException(String message) {
            super(message);
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.BAD_REQUEST;
        }

        @Override
        public String code() {
            return "INVALID_INPUT";
        }
    }

    public static class ExternalServiceException extends CampusOpsException {
        public ExternalServiceException(String service, Throwable cause) {
            super(service + " could not be reached", cause);
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.BAD_GATEWAY;
        }

        @Override
        public String code() {
            return "EXTERNAL_SERVICE_FAILURE";
        }
    }
}
