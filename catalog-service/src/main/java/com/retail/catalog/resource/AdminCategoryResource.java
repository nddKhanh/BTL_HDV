package com.retail.catalog.resource;

import com.retail.catalog.dto.CategoryDto.CategoryRequest;
import com.retail.catalog.dto.CategoryDto.CategoryResponse;
import com.retail.catalog.service.CategoryService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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

@Path("/api/admin/categories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Categories (admin)")
@SecurityScheme(securitySchemeName = "adminKey", type = SecuritySchemeType.APIKEY, apiKeyName = "X-Admin-Key", in = SecuritySchemeIn.HEADER)
@SecurityRequirement(name = "adminKey")
public class AdminCategoryResource {

    @Inject
    CategoryService service;

    @POST
    public Response create(@NotNull(message = "request body is required") @Valid CategoryRequest request) {
        CategoryResponse created = service.create(request);
        return Response.created(URI.create("/api/categories/" + created.id())).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    public CategoryResponse update(@PathParam("id") String id,
                                   @NotNull(message = "request body is required") @Valid CategoryRequest request) {
        return service.update(id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
