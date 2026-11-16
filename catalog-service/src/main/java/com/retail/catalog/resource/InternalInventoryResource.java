package com.retail.catalog.resource;

import com.retail.catalog.dto.ReservationDto.ReservationResponse;
import com.retail.catalog.dto.ReservationDto.ReserveOutcome;
import com.retail.catalog.dto.ReservationDto.ReserveRequest;
import com.retail.catalog.service.InventoryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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

@Path("/internal/stock/reservations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Internal Inventory (C2/C3/C4)")
@SecurityScheme(
        securitySchemeName = "internalKey",
        type = SecuritySchemeType.APIKEY,
        apiKeyName = "X-Internal-Key",
        in = SecuritySchemeIn.HEADER
)
@SecurityRequirement(name = "internalKey")
public class InternalInventoryResource {

    @Inject
    InventoryService inventoryService;

    @POST
    public Response reserve(ReserveRequest request) {
        ReserveOutcome outcome = inventoryService.reserve(request);
        int status = outcome.created() ? Response.Status.CREATED.getStatusCode() : Response.Status.OK.getStatusCode();
        return Response.status(status).entity(outcome.response()).build();
    }

    @GET
    @Path("/{orderId}")
    public ReservationResponse get(@PathParam("orderId") String orderId) {
        return inventoryService.get(orderId);
    }

    @POST
    @Path("/{orderId}/release")
    @Consumes(MediaType.WILDCARD)
    public ReservationResponse release(@PathParam("orderId") String orderId) {
        return inventoryService.release(orderId);
    }
}
