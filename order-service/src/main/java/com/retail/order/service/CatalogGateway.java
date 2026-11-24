package com.retail.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.order.client.CatalogClient;
import com.retail.order.dto.QuoteDtos;
import com.retail.order.dto.ReservationDtos;
import com.retail.order.exception.CatalogBusinessException;
import com.retail.order.exception.CatalogUnavailableException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class CatalogGateway {
    private static final Logger LOG = Logger.getLogger(CatalogGateway.class);

    // Only deterministic product/stock validation errors reject a PENDING checkout.
    // Reservation conflicts indicate an integrity/reconciliation problem and must stay pending.
    private static final Set<String> QUOTE_BUSINESS_CODES = Set.of(
            "PRODUCT_NOT_FOUND", "PRODUCT_INACTIVE", "INVALID_ITEMS");
    private static final Set<String> RESERVE_BUSINESS_CODES = Set.of(
            "PRODUCT_NOT_FOUND", "PRODUCT_INACTIVE", "OUT_OF_STOCK", "PRICE_CHANGED");

    @Inject @RestClient CatalogClient catalog;
    @Inject ObjectMapper objectMapper;
    @ConfigProperty(name = "app.internal-key") String internalKey;

    public QuoteDtos.Response quote(QuoteDtos.Request request) {
        try {
            QuoteDtos.Response response = catalog.quote(internalKey, request);
            if (response == null || response.items() == null || response.totalVnd() < 0) {
                throw new CatalogUnavailableException("Catalog returned an invalid quote response");
            }
            return response;
        } catch (WebApplicationException ex) {
            throw classify(ex, QUOTE_BUSINESS_CODES);
        } catch (CatalogUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new CatalogUnavailableException("Catalog quote request failed", ex);
        }
    }

    public ReservationDtos.Response reserve(ReservationDtos.Request request) {
        try {
            ReservationDtos.Response response = catalog.reserve(internalKey, request);
            if (response == null || response.status() == null
                    || !request.orderId().equals(response.orderId())) {
                throw new CatalogUnavailableException("Catalog returned an invalid reservation response");
            }
            return response;
        } catch (WebApplicationException ex) {
            throw classify(ex, RESERVE_BUSINESS_CODES);
        } catch (CatalogUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new CatalogUnavailableException("Catalog reserve request failed", ex);
        }
    }

    public ReservationDtos.Response getReservation(UUID orderId) {
        try {
            ReservationDtos.Response response = catalog.getReservation(internalKey, orderId);
            if (response == null || response.status() == null || !orderId.equals(response.orderId())) {
                throw new CatalogUnavailableException("Catalog returned an invalid reservation response");
            }
            return response;
        } catch (WebApplicationException ex) {
            // C3 uses 404 RESERVATION_NOT_FOUND to mean that C2 has not committed
            // a reservation yet. This is an expected reconciliation result, not
            // a permanent Catalog failure and must not reject the Order.
            if (isReservationNotFound(ex)) return null;
            throw classify(ex, Set.of());
        } catch (CatalogUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new CatalogUnavailableException("Catalog reservation lookup failed", ex);
        }
    }

    private boolean isReservationNotFound(WebApplicationException ex) {
        if (ex.getResponse() == null || ex.getResponse().getStatus() != 404) return false;
        try {
            String body = ex.getResponse().readEntity(String.class);
            JsonNode root = objectMapper.readTree(body);
            return "RESERVATION_NOT_FOUND".equals(root.path("error").path("code").asText());
        } catch (Exception ignored) {
            return false;
        }
    }

    public ReservationDtos.Response release(UUID orderId) {
        try {
            ReservationDtos.Response response = catalog.release(internalKey, orderId);
            if (response == null || response.status() == null || !orderId.equals(response.orderId())) {
                throw new CatalogUnavailableException("Catalog returned an invalid release response");
            }
            return response;
        } catch (WebApplicationException ex) {
            throw classify(ex, Set.of());
        } catch (CatalogUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new CatalogUnavailableException("Catalog release request failed", ex);
        }
    }

    private RuntimeException classify(WebApplicationException ex, Set<String> businessCodes) {
        int status = ex.getResponse() == null ? 503 : ex.getResponse().getStatus();
        String code = "CATALOG_UNAVAILABLE";
        String message = "Catalog service is unavailable";
        try {
            String body = ex.getResponse().readEntity(String.class);
            JsonNode root = objectMapper.readTree(body);
            code = root.path("error").path("code").asText(code);
            message = root.path("error").path("message").asText(message);
        } catch (Exception ignored) {
            // Keep safe generic message.
        }
        if (status >= 400 && status < 500 && businessCodes.contains(code)) {
            return new CatalogBusinessException(code, status, message);
        }
        LOG.warnf("Catalog returned HTTP %d with code %s", status, code);
        return new CatalogUnavailableException("Catalog service is unavailable");
    }
}
