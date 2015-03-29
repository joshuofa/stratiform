package org.atomicsauce.stratiform.exceptions;

public final class StratiformRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StratiformRuntimeException() {
        super();
    }

    public StratiformRuntimeException( String message ) {
        super( message );
    }

    public StratiformRuntimeException( Throwable cause ) {
        super( cause );
    }

    public StratiformRuntimeException( String message, Throwable cause ) {
        super( message, cause );
    }

    public StratiformRuntimeException( String message,
                                       Throwable cause,
                                       boolean enable_suppression,
                                       boolean writable_stack_trace) {
        super( message, cause, enable_suppression, writable_stack_trace );
    }
}
