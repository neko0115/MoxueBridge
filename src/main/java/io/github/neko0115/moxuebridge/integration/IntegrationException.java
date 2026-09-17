package io.github.neko0115.moxuebridge.integration;

public final class IntegrationException extends Exception {

    public IntegrationException(String message) {
        super(message);
    }

    public IntegrationException(
            String message,
            Throwable cause) {
        super(message, cause);
    }
}