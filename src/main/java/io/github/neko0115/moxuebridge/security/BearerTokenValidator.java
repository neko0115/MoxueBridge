package io.github.neko0115.moxuebridge.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

public final class BearerTokenValidator {

    private static final String PREFIX = "Bearer ";

    private final byte[] expectedToken;

    public BearerTokenValidator(String token) {
        Objects.requireNonNull(token, "token");
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isAuthorized(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.startsWith(PREFIX)) {
            return false;
        }

        String suppliedToken =
                authorizationHeader.substring(PREFIX.length());

        byte[] suppliedBytes =
                suppliedToken.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(
                expectedToken,
                suppliedBytes);
    }
}