package com.retail.payment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.*;

@Path("/") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {
    @PersistenceContext EntityManager em;
    public record PaymentRequest(@NotNull UUID orderId, @PositiveOrZero long amountVnd, @NotBlank @Size(max=30) String method, boolean simulateFailure) {}
    @GET @Path("api/ping") @Produces(MediaType.TEXT_PLAIN) public String ping() { return "Hello from Payment Service"; }
    @POST @Path("internal/payments") @Transactional public Map<String,Object> charge(@jakarta.validation.Valid PaymentRequest r) {
        List<?> rows = em.createNativeQuery("select amount_vnd,status,method from payments where order_id=?1").setParameter(1,r.orderId()).getResultList();
        if (!rows.isEmpty()) { Object[] v=(Object[]) rows.getFirst(); if (((Number)v[0]).longValue()!=r.amountVnd() || !v[2].equals(r.method())) throw new ClientErrorException("Payment request conflicts with existing payment",409); return response(r.orderId(), ((Number)v[0]).longValue(), (String)v[1], (String)v[2]); }
        String status = r.simulateFailure() ? "DECLINED" : "AUTHORIZED";
        em.createNativeQuery("insert into payments(order_id,amount_vnd,status,method) values (?1,?2,?3,?4)").setParameter(1,r.orderId()).setParameter(2,r.amountVnd()).setParameter(3,status).setParameter(4,r.method()).executeUpdate();
        return response(r.orderId(),r.amountVnd(),status,r.method());
    }
    @POST @Path("internal/payments/{orderId}/refund") @Transactional public Map<String,Object> refund(@PathParam("orderId") UUID id) {
        int updated=em.createNativeQuery("update payments set status='REFUNDED',updated_at=now() where order_id=?1 and status='AUTHORIZED'").setParameter(1,id).executeUpdate();
        if(updated==0) { List<?> rows=em.createNativeQuery("select amount_vnd,status,method from payments where order_id=?1").setParameter(1,id).getResultList(); if(rows.isEmpty()) throw new NotFoundException("Payment not found"); Object[] v=(Object[])rows.getFirst(); return response(id,((Number)v[0]).longValue(),(String)v[1],(String)v[2]); }
        List<?> rows=em.createNativeQuery("select amount_vnd,method from payments where order_id=?1").setParameter(1,id).getResultList(); Object[] v=(Object[])rows.getFirst(); return response(id,((Number)v[0]).longValue(),"REFUNDED",(String)v[1]);
    }
    @GET @Path("internal/payments/{orderId}") public Map<String,Object> get(@PathParam("orderId") UUID id) { List<?> rows=em.createNativeQuery("select amount_vnd,status,method from payments where order_id=?1").setParameter(1,id).getResultList(); if(rows.isEmpty()) throw new NotFoundException("Payment not found"); Object[] v=(Object[])rows.getFirst(); return response(id,((Number)v[0]).longValue(),(String)v[1],(String)v[2]); }
    private static Map<String,Object> response(UUID id,long amount,String status,String method) { return Map.of("orderId",id,"amountVnd",amount,"status",status,"method",method); }
}
