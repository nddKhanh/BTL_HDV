package com.retail.ai;

import com.retail.ai.resource.HealthCheckResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthCheckResourceTest {

    @Test
    void helloReturnsGreeting() {
        HealthCheckResource resource = new HealthCheckResource();
        assertEquals("Hello from AI RAG Service", resource.hello());
    }
}
