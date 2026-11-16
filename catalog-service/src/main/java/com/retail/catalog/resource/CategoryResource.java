package com.retail.catalog.resource;

import com.retail.catalog.dto.CategoryDto.CategoryResponse;
import com.retail.catalog.service.CategoryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.List;

@Path("/api/categories")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Categories (public)")
public class CategoryResource {

    @Inject
    CategoryService service;

    @GET
    public List<CategoryResponse> list() {
        return service.listAll();
    }

    @GET
    @Path("/{id}")
    public CategoryResponse get(@PathParam("id") String id) {
        return service.get(id);
    }
}
