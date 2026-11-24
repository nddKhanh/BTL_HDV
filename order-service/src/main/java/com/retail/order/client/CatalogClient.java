package com.retail.order.client;

import com.retail.order.dto.QuoteDtos;
import com.retail.order.dto.ReservationDtos;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.UUID;

@Path("/internal")
@RegisterRestClient(configKey = "catalog-api")
public interface CatalogClient {
    @POST
    @Path("/catalog/quote")
    QuoteDtos.Response quote(@HeaderParam("X-Internal-Key") String key, QuoteDtos.Request request);

    @POST
    @Path("/stock/reservations")
    ReservationDtos.Response reserve(@HeaderParam("X-Internal-Key") String key, ReservationDtos.Request request);

    @GET
    @Path("/stock/reservations/{orderId}")
    ReservationDtos.Response getReservation(@HeaderParam("X-Internal-Key") String key, @PathParam("orderId") UUID orderId);

    @POST
    @Path("/stock/reservations/{orderId}/release")
    ReservationDtos.Response release(@HeaderParam("X-Internal-Key") String key, @PathParam("orderId") UUID orderId);
}
