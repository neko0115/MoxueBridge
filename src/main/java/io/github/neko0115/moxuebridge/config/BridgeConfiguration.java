package io.github.neko0115.moxuebridge.config;

import java.util.Objects;
import java.util.function.Supplier;

public record BridgeConfiguration(
        String bindAddress,
        int port,
        String token) {

    public static BridgeConfiguration resolve(
            String bindAddress,
            int port,
            String token,
            Supplier<String> tokenSupplier) {

        Objects.requireNonNull(
                tokenSupplier,
                "tokenSupplier");

        String resolvedBind =
                bindAddress == null
                        ? ""
                        : bindAddress.trim();

        if (resolvedBind.isBlank()) {
            throw new IllegalArgumentException(
                    "HTTP bind address must not be blank");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "HTTP port must be between 1 and 65535");
        }

        String configuredToken =
                token == null
                        ? ""
                        : token.trim();

        String resolvedToken;

        if (configuredToken.isBlank()) {
            resolvedToken =
                    Objects.requireNonNull(
                            tokenSupplier.get(),
                            "generated token")
                            .trim();

            if (resolvedToken.isBlank()) {
                throw new IllegalArgumentException(
                        "Generated API token must not be blank");
            }
        } else {
            resolvedToken = configuredToken;
        }

        return new BridgeConfiguration(
                resolvedBind,
                port,
                resolvedToken);
    }
}