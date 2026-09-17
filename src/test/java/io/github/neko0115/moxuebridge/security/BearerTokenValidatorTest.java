package io.github.neko0115.moxuebridge.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.neko0115.moxuebridge.config.TokenGenerator;
import org.junit.jupiter.api.Test;

class BearerTokenValidatorTest {

    @Test
    void acceptsOnlyExactBearerToken() {
        var validator = new BearerTokenValidator("abc123");

        assertTrue(validator.isAuthorized("Bearer abc123"));

        assertFalse(validator.isAuthorized(null));
        assertFalse(validator.isAuthorized("abc123"));
        assertFalse(validator.isAuthorized("Bearer wrong"));
    }

    @Test
    void generatedTokenIsLongAndUrlSafe() {
        var token = new TokenGenerator().generate();

        assertTrue(token.length() >= 43);
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
    }
}