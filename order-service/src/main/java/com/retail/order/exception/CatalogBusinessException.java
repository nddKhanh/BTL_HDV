package com.retail.order.exception;

public class CatalogBusinessException extends RuntimeException {
    private final String code;
    private final int status;

    public CatalogBusinessException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
    public String code() { return code; }
    public int status() { return status; }
}
