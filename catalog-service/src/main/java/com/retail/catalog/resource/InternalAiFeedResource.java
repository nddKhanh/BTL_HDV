package com.retail.catalog.resource;

import com.retail.catalog.dto.KnowledgeFeedDto.KnowledgeFeedResponse;
import com.retail.catalog.service.KnowledgeExportService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeIn;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/internal/ai/products")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Internal AI Knowledge Feed (C5)")
@SecurityScheme(
        securitySchemeName = "internalKey",
        type = SecuritySchemeType.APIKEY,
        apiKeyName = "X-Internal-Key",
        in = SecuritySchemeIn.HEADER
)
@SecurityRequirement(name = "internalKey")
public class InternalAiFeedResource {

    @Inject
    KnowledgeExportService knowledgeExportService;

    @GET
    public KnowledgeFeedResponse list(
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        return knowledgeExportService.page(offset, limit);
    }
}
