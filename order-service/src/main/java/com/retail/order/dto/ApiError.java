package com.retail.order.dto;

public record ApiError(ErrorBody error) {
    public record ErrorBody(String code, String message, String requestId) { }
}
