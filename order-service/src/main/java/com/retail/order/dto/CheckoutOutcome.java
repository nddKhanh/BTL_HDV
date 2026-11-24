package com.retail.order.dto;

public record CheckoutOutcome(CheckoutResponse body, int httpStatus) { }
