package com.retail.order.exception;

public class OrderApiException extends RuntimeException {
    private final int httpStatus;
    private final String code;

    public OrderApiException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int httpStatus() { return httpStatus; }
    public String code() { return code; }
}
