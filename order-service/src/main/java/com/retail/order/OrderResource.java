package com.retail.order;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Path("/api/orders") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {
    @PersistenceContext EntityManager em;
    @Inject ObjectMapper json;
    @ConfigProperty(name="app.internal-key") String key;
    @ConfigProperty(name="app.catalog-base-url") String catalog;
    @ConfigProperty(name="app.payment-base-url") String payment;
    @ConfigProperty(name="app.shipping-base-url") String shipping;
    @ConfigProperty(name="app.notification-base-url") String notification;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public record Item(@NotNull UUID productId,@Min(1) @Max(99) int quantity) {}
    public record CreateOrder(@Email @NotBlank String customerEmail,@NotBlank @Size(max=1000) String shippingAddress,@NotNull @Size(min=1,max=20) List<Item> items) {}
    public record Pay(@NotBlank @Size(max=30) String method,boolean simulateFailure) {}
    @POST @Transactional public Response create(@jakarta.validation.Valid CreateOrder in){
        Map<String,Object> quote=post(catalog+"/internal/catalog/quote",Map.of("items",in.items()));
        UUID id=UUID.randomUUID();String checkout=UUID.randomUUID().toString().replace("-","");long total=((Number)quote.get("totalVnd")).longValue();String currency=(String)quote.get("currency");
        em.createNativeQuery("insert into orders(id,checkout_key,customer_email,shipping_address,status,total_vnd,currency) values(?1,?2,?3,?4,'PENDING',?5,?6)").setParameter(1,id).setParameter(2,checkout).setParameter(3,in.customerEmail()).setParameter(4,in.shippingAddress()).setParameter(5,total).setParameter(6,currency).executeUpdate();
        @SuppressWarnings("unchecked") List<Map<String,Object>> lines=(List<Map<String,Object>>)quote.get("items");
        List<Map<String,Object>> reserve=new ArrayList<>();for(Map<String,Object> line:lines){UUID productId=UUID.fromString(line.get("productId").toString());int quantity=((Number)line.get("quantity")).intValue();long price=((Number)line.get("unitPriceVnd")).longValue();em.createNativeQuery("insert into order_items(order_id,product_id,sku,name,quantity,unit_price_vnd) values(?1,?2,?3,?4,?5,?6)").setParameter(1,id).setParameter(2,productId).setParameter(3,line.get("sku")).setParameter(4,line.get("name")).setParameter(5,quantity).setParameter(6,price).executeUpdate();reserve.add(Map.of("productId",productId,"quantity",quantity,"expectedUnitPriceVnd",price));}
        post(catalog+"/internal/stock/reservations",Map.of("orderId",id,"items",reserve));em.createNativeQuery("update orders set status='CONFIRMED',updated_at=now() where id=?1").setParameter(1,id).executeUpdate();notify(id,in.customerEmail(),"ORDER_CONFIRMED","Your order is confirmed and awaiting payment.");return Response.status(201).entity(detail(id,checkout)).build();
    }
    @GET @Path("/{id}") public Map<String,Object> get(@PathParam("id") UUID id,@HeaderParam("X-Checkout-Key") String checkout){return detail(id,requireKey(id,checkout));}
    @POST @Path("/{id}/pay") @Transactional public Map<String,Object> pay(@PathParam("id") UUID id,@HeaderParam("X-Checkout-Key") String checkout,@jakarta.validation.Valid Pay request){String valid=requireKey(id,checkout);Map<String,Object> order=detail(id,valid);if(!"CONFIRMED".equals(order.get("status"))&&!"PAID".equals(order.get("status")))throw new ClientErrorException("Order cannot be paid in its current state",409);Map<String,Object> p=post(payment+"/internal/payments",Map.of("orderId",id,"amountVnd",order.get("totalVnd"),"method",request.method(),"simulateFailure",request.simulateFailure()));if(!"AUTHORIZED".equals(p.get("status")))return Map.of("order",order,"payment",p);em.createNativeQuery("update orders set status='PAID',updated_at=now() where id=?1").setParameter(1,id).executeUpdate();Map<String,Object>s=post(shipping+"/internal/shipments",Map.of("orderId",id,"address",order.get("shippingAddress")));notify(id,(String)order.get("customerEmail"),"PAYMENT_AUTHORIZED","Payment was authorized. Tracking code: "+s.get("trackingCode"));return Map.of("order",detail(id,valid),"payment",p,"shipment",s);}
    @POST @Path("/{id}/cancel") @Transactional public Map<String,Object> cancel(@PathParam("id") UUID id,@HeaderParam("X-Checkout-Key") String checkout){String valid=requireKey(id,checkout);Map<String,Object> order=detail(id,valid);if("CANCELLED".equals(order.get("status")))return order;if(!Set.of("CONFIRMED","PAID").contains(order.get("status")))throw new ClientErrorException("Order cannot be cancelled in its current state",409);if("PAID".equals(order.get("status"))){post(shipping+"/internal/shipments/"+id+"/cancel",Map.of());post(payment+"/internal/payments/"+id+"/refund",Map.of());}post(catalog+"/internal/stock/reservations/"+id+"/release",Map.of());em.createNativeQuery("update orders set status='CANCELLED',updated_at=now() where id=?1").setParameter(1,id).executeUpdate();notify(id,(String)order.get("customerEmail"),"ORDER_CANCELLED","Your order was cancelled.");return detail(id,valid);}
    private String requireKey(UUID id,String supplied){List<?>q=em.createNativeQuery("select checkout_key from orders where id=?1").setParameter(1,id).getResultList();if(q.isEmpty())throw new NotFoundException("Order not found");String actual=(String)q.getFirst();if(supplied==null||!java.security.MessageDigest.isEqual(actual.getBytes(),supplied.getBytes()))throw new NotAuthorizedException("Invalid checkout key");return actual;}
    private Map<String,Object> detail(UUID id,String checkout){List<?>q=em.createNativeQuery("select customer_email,shipping_address,status,total_vnd,currency,created_at from orders where id=?1").setParameter(1,id).getResultList();if(q.isEmpty())throw new NotFoundException("Order not found");Object[]o=(Object[])q.getFirst();List<?>l=em.createNativeQuery("select product_id,sku,name,quantity,unit_price_vnd from order_items where order_id=?1 order by sku").setParameter(1,id).getResultList();List<Map<String,Object>>items=new ArrayList<>();for(Object x:l){Object[]v=(Object[])x;items.add(Map.of("productId",v[0],"sku",v[1],"name",v[2],"quantity",v[3],"unitPriceVnd",v[4]));}Map<String,Object>r=new LinkedHashMap<>();r.put("id",id);r.put("checkoutKey",checkout);r.put("customerEmail",o[0]);r.put("shippingAddress",o[1]);r.put("status",o[2]);r.put("totalVnd",o[3]);r.put("currency",o[4]);r.put("createdAt",o[5].toString());r.put("items",items);return r;}
    private void notify(UUID id,String email,String event,String message){try{post(notification+"/internal/notifications",Map.of("orderId",id,"recipient",email,"eventType",event,"message",message));}catch(RuntimeException ignored){}}
    private Map<String,Object> post(String url,Object body){try{String text=json.writeValueAsString(body);HttpRequest r=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").header("X-Internal-Key",key).POST(HttpRequest.BodyPublishers.ofString(text)).build();HttpResponse<String>res=http.send(r,HttpResponse.BodyHandlers.ofString());if(res.statusCode()<200||res.statusCode()>=300)throw new BadGatewayException("Dependency request failed: "+url);return json.readValue(res.body(),new TypeReference<>(){});}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ServiceUnavailableException("Dependency request interrupted");}catch(Exception e){if(e instanceof WebApplicationException w)throw w;throw new ServiceUnavailableException("Dependency request failed");}}
}
