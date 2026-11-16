package com.retail.catalog.service;

import com.retail.catalog.exception.ApiException;
import jakarta.persistence.PersistenceException;
import java.util.UUID;

final class ServiceSupport {

    private ServiceSupport() {
    }

    /** Parses a path/query UUID; an unparsable id is treated as "not found". */
    static UUID parseIdOrNotFound(String raw, String code, String message) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.notFound(code, message);
        }
    }

    static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    static boolean isConstraintViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException) return true;
            if (t == t.getCause()) break;
        }
        return false;
    }

    static void rethrowIfNotConstraint(PersistenceException e) {
        if (!isConstraintViolation(e)) throw e;
    }
}
