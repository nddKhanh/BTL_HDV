package com.retail.order.exception;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class RequestContextData {
    private String requestId;
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
