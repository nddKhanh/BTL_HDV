package com.retail.order.exception;

public class CatalogUnavailableException extends RuntimeException {
    public CatalogUnavailableException(String message, Throwable cause) { super(message, cause); }
    public CatalogUnavailableException(String message) { super(message); }
}
