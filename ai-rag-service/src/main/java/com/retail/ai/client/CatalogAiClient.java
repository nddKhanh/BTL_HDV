package com.retail.ai.client;

import com.retail.ai.dto.ProductFeedResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/internal/ai")
@RegisterRestClient(configKey = "catalog-api")
public interface CatalogAiClient {
    @GET
    @Path("/products")
    ProductFeedResponse products(@HeaderParam("X-Internal-Key") String key,
                                 @QueryParam("offset") int offset,
                                 @QueryParam("limit") int limit);
}
