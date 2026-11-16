package com.retail.catalog.resource;

import com.retail.catalog.dto.ProductDto.ProductCreateRequest;
import com.retail.catalog.dto.ProductDto.ProductResponse;
import com.retail.catalog.dto.ProductDto.ProductUpdateRequest;
import com.retail.catalog.dto.ProductDto.StockUpdateRequest;
import com.retail.catalog.service.ProductService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeIn;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.net.URI;

@Path("/api/admin/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Products (admin)")
@SecurityScheme(securitySchemeName = "adminKey", type = SecuritySchemeType.APIKEY, apiKeyName = "X-Admin-Key", in = SecuritySchemeIn.HEADER)
@SecurityRequirement(name = "adminKey")
public class AdminProductResource {

    @Inject
    ProductService service;

    @POST
    public Response create(@NotNull(message = "request body is required") @Valid ProductCreateRequest request) {
        ProductResponse created = service.create(request);
        return Response.created(URI.create("/api/products/" + created.id())).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    public ProductResponse update(@PathParam("id") String id,
                                  @NotNull(message = "request body is required") @Valid ProductUpdateRequest request) {
        return service.update(id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        service.deactivate(id);
        return Response.noContent().build();
    }

    @PATCH
    @Path("/{id}/stock")
    public ProductResponse updateStock(@PathParam("id") String id,
                                       @NotNull(message = "request body is required") @Valid StockUpdateRequest request) {
        return service.updateStock(id, request);
    }
}
