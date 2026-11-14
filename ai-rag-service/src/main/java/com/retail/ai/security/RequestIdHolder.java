package com.retail.ai.security;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class RequestIdHolder {
    public String value;
}
