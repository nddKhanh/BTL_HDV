package com.retail.catalog.security;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Holds the static API secrets and fails fast at startup when one is blank. */
@ApplicationScoped
public class ApiKeys {

    @ConfigProperty(name = "app.admin-key")
    String adminKey;

    @ConfigProperty(name = "app.internal-key")
    String internalKey;

    void onStart(@Observes StartupEvent event) {
        requireSecret("app.admin-key (ADMIN_API_KEY)", adminKey);
        requireSecret("app.internal-key (INTERNAL_API_KEY)", internalKey);
    }

    public boolean isValidAdminKey(String supplied) {
        return constantTimeEquals(adminKey, supplied);
    }

    public boolean isValidInternalKey(String supplied) {
        return constantTimeEquals(internalKey, supplied);
    }

    private static void requireSecret(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required secret: " + name);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || expected.isBlank() || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
