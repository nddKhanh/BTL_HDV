package com.retail.order;

import jakarta.ws.rs.ServiceUnavailableException;

class BadGatewayException extends ServiceUnavailableException {
    BadGatewayException(String message) {
        super(message);
    }
}
