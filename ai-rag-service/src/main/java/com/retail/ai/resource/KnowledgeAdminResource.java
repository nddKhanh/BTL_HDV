package com.retail.ai.resource;

import com.retail.ai.dto.IndexResponse;
import com.retail.ai.dto.KnowledgeDocumentRequest;
import com.retail.ai.service.IndexService;
import com.retail.ai.service.KnowledgeService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeIn;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.Map;

@Path("/api/admin/knowledge")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Knowledge Base (admin)")
@SecurityScheme(
        securitySchemeName = "adminKey",
        type = SecuritySchemeType.APIKEY,
        apiKeyName = "X-Admin-Key",
        in = SecuritySchemeIn.HEADER
)
@SecurityRequirement(name = "adminKey")
public class KnowledgeAdminResource {
    @Inject KnowledgeService knowledge;
    @Inject IndexService index;

    @POST
    @Path("/documents")
    @Blocking
    @Operation(summary = "Upsert knowledge document (A2)", description = "Save or update a FAQ/policy document without triggering full reindex")
    public Response upsert(KnowledgeDocumentRequest request) {
        boolean created = knowledge.saveFaq(request);
        return Response.status(created ? 201 : 200)
                .entity(Map.of("sourceId", request.sourceId().trim(), "status", "SAVED"))
                .build();
    }

    @POST
    @Path("/reindex")
    @Blocking
    @Operation(summary = "Reindex knowledge store (A3)", description = "Fetch Catalog C5 active products and rebuild pgvector embeddings")
    public IndexResponse reindex() {
        return index.reindex();
    }
}
