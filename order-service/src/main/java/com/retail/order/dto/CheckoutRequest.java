package com.retail.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CheckoutRequest(
        @NotNull @Valid Customer customer,
        @NotEmpty @Size(max = 20) List<@NotNull @Valid Item> items
) {
    public record Customer(
            @jakarta.validation.constraints.NotBlank @Size(max = 120) String name,
            @jakarta.validation.constraints.NotBlank @Size(max = 20) String phone,
            @jakarta.validation.constraints.NotBlank @Size(max = 500) String address
    ) { }

    public record Item(
            @NotNull UUID productId,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(99) int quantity
    ) { }
}
