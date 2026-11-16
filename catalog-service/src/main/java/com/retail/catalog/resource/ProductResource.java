package com.retail.catalog.resource;

import com.retail.catalog.dto.ProductDto.ProductPage;
import com.retail.catalog.dto.ProductDto.ProductResponse;
import com.retail.catalog.service.ProductService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/products")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Products (public)")
public class ProductResource {

    @Inject
    ProductService service;

    @GET
    public ProductPage search(@QueryParam("page") @DefaultValue("0") int page,
                              @QueryParam("size") @DefaultValue("20") int size,
                              @QueryParam("query") String query,
                              @QueryParam("categoryId") String categoryId) {
        return service.search(page, size, query, categoryId);
    }

    @GET
    @Path("/{id}")
    public ProductResponse get(@PathParam("id") String id) {
        return service.getActive(id);
    }
}
