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
import java.util.Map;

@Path("/api/admin/knowledge")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class KnowledgeAdminResource {
    @Inject KnowledgeService knowledge;
    @Inject IndexService index;

    @POST
    @Path("/documents")
    @Blocking
    public Response upsert(KnowledgeDocumentRequest request) {
        boolean created = knowledge.saveFaq(request);
        return Response.status(created ? 201 : 200)
                .entity(Map.of("sourceId", request.sourceId().trim(), "status", "SAVED"))
                .build();
    }

    @POST
    @Path("/reindex")
    @Blocking
    public IndexResponse reindex() {
        return index.reindex();
    }
}
