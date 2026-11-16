package com.retail.catalog.resource;

import com.retail.catalog.dto.QuoteDto.QuoteRequest;
import com.retail.catalog.dto.QuoteDto.QuoteResponse;
import com.retail.catalog.service.QuoteService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeIn;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/internal/catalog")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Internal Catalog (C1)")
@SecurityScheme(
        securitySchemeName = "internalKey",
        type = SecuritySchemeType.APIKEY,
        apiKeyName = "X-Internal-Key",
        in = SecuritySchemeIn.HEADER
)
@SecurityRequirement(name = "internalKey")
public class InternalCatalogResource {

    @Inject
    QuoteService quoteService;

    @POST
    @Path("/quote")
    public QuoteResponse quote(QuoteRequest request) {
        return quoteService.quote(request);
    }
}
