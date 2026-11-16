package com.retail.catalog.service;

import com.retail.catalog.dto.QuoteDto.QuoteItemRequest;
import com.retail.catalog.dto.QuoteDto.QuoteLineResponse;
import com.retail.catalog.dto.QuoteDto.QuoteRequest;
import com.retail.catalog.dto.QuoteDto.QuoteResponse;
import com.retail.catalog.entity.Product;
import com.retail.catalog.repository.ProductRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C1: price snapshot for Order. Read-only — never changes stock and never writes to the DB.
 * Prices always come from catalog_db; any client-supplied price is ignored.
 */
@ApplicationScoped
public class QuoteService {

    @Inject
    ProductRepository products;

    public QuoteResponse quote(QuoteRequest request) {
        List<QuoteItemRequest> items = request == null ? null : request.items();
        ItemRules.requireLineCount(items);
        items.forEach(i -> ItemRules.requireLine(i.productId(), i.quantity()));
        List<UUID> ids = items.stream().map(QuoteItemRequest::productId).toList();
        ItemRules.requireUnique(ids);

        Map<UUID, Product> byId = products.listByIds(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (UUID id : ids) {
            if (!byId.containsKey(id)) throw ItemRules.productNotFound(id);
        }
        for (UUID id : ids) {
            if (!Boolean.TRUE.equals(byId.get(id).getActive())) throw ItemRules.productInactive(id);
        }

        List<QuoteLineResponse> lines = new ArrayList<>(items.size());
        long total = 0L;
        try {
            for (QuoteItemRequest item : items) {
                Product p = byId.get(item.productId());
                long unit = p.getPriceVnd();
                long lineTotal = Math.multiplyExact(unit, (long) item.quantity());
                total = Math.addExact(total, lineTotal);
                lines.add(new QuoteLineResponse(p.getId(), p.getSku(), p.getName(),
                        item.quantity(), unit, lineTotal));
            }
        } catch (ArithmeticException overflow) {
            throw ItemRules.invalid("order total exceeds the supported amount");
        }
        return new QuoteResponse("VND", lines, total);
    }
}
