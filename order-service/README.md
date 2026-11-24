# Order Service — Java 21 + Quarkus 3 + PostgreSQL

Dự án được dựng theo `GUIDE_Order_Service.md`, Contract V1.0. Host port: **8082**. Database riêng: **order_db**. Khi chưa có Catalog Service, project mặc định gọi Mock Catalog nội bộ tại `http://localhost:8082/mock-catalog`.

## 1. Yêu cầu

- JDK 21 (IntelliJ Project SDK = 21)
- IntelliJ IDEA có Maven support
- Docker Desktop đang chạy (để chạy PostgreSQL)
- Maven 3.9+ hoặc Maven tích hợp của IntelliJ

## 2. Mở project bằng IntelliJ

1. Giải nén thư mục `order-service`.
2. IntelliJ → **File → Open** → chọn thư mục `order-service` (thư mục chứa `pom.xml`).
3. Chọn **Trust Project** nếu được hỏi; chọn **Load Maven Project**.
4. File → Project Structure → Project SDK chọn JDK 21; Language level chọn 21.
5. Chờ Maven tải dependencies xong.

## 3. Khởi động PostgreSQL

Mở Terminal trong IntelliJ tại thư mục gốc project:

```powershell
Copy-Item .env.example .env
# Dùng cùng mật khẩu ở PostgreSQL và Order Service trong terminal hiện tại.
$env:ORDER_DB_PASSWORD = "orders_dev_password"
$env:INTERNAL_API_KEY = "dev-local-key-change-this-before-sharing"
docker compose -f compose.db.yaml up -d
docker compose -f compose.db.yaml ps
```

Port mapping: `localhost:5434` → PostgreSQL container `5432`. Flyway tự tạo bảng khi ứng dụng khởi động.

## 4. Chạy Order Service

Trong IntelliJ mở Maven tool window → Plugins → quarkus → quarkus:dev (nếu plugin chưa hiển thị, chạy Terminal). Trước khi chạy dev, đặt `ORDER_DB_PASSWORD` và `INTERNAL_API_KEY` như mục kiểm thử; profile `dev` tự bật Mock Catalog nếu không ghi đè `MOCK_CATALOG_ENABLED`.

```powershell
mvn quarkus:dev
```

Ứng dụng: `http://localhost:8082`  
Swagger UI: `http://localhost:8082/q/swagger-ui`  
OpenAPI: `http://localhost:8082/q/openapi`  
Health: `http://localhost:8082/q/health`

## 5. Thử checkout bằng Mock Catalog

Mock có một sản phẩm mẫu:

- productId: `11111111-1111-4111-8111-111111111111`
- SKU: `TS-001`
- Tên: `Ao thun basic (Mock Catalog)`
- Giá: `199000` VND
- Tồn ban đầu của mock: 100 sản phẩm (in-memory; reset khi restart app)

Header `X-Checkout-Key` phải là UUID v4. Dùng ví dụ mới dưới đây cho một lần checkout:

```http
POST http://localhost:8082/api/orders
Content-Type: application/json
X-Checkout-Key: 33333333-3333-4333-8333-333333333333
```

```json
{
  "customer": {
    "name": "Nguyen Van A",
    "phone": "0912345678",
    "address": "Ha Noi"
  },
  "items": [
    { "productId": "11111111-1111-4111-8111-111111111111", "quantity": 2 }
  ]
}
```

Kết quả dự kiến lần đầu: `201 Created`, `status=CONFIRMED`, `totalVnd=398000`. Lưu lại `id` và checkout key để GET hoặc cancel. Không dùng lại key ví dụ này với nội dung khác.

### Tra cứu đơn

```http
GET http://localhost:8082/api/orders/{ORDER_ID}
X-Checkout-Key: 33333333-3333-4333-8333-333333333333
```

### Hủy đơn

```http
POST http://localhost:8082/api/orders/{ORDER_ID}/cancel
X-Checkout-Key: 33333333-3333-4333-8333-333333333333
```

Kết quả dự kiến: `200 OK`, `status=CANCELLED`. Gọi lặp không hoàn kho hai lần.

## 6. Kiểm thử

Database phải đang chạy. Nếu mở terminal IntelliJ mới để chạy profile `dev`, đặt lại các biến môi trường (profile `dev` tự bật Mock Catalog nếu không ghi đè):

```powershell
$env:ORDER_DB_PASSWORD = "orders_dev_password"
$env:INTERNAL_API_KEY = "dev-local-key-change-this-before-sharing"
$env:MOCK_CATALOG_ENABLED = "true"
```

Để chạy kiểm thử, Quarkus dùng các giá trị riêng của `%test` profile nên không cần dùng secret dev. Dừng tiến trình `quarkus:dev` trước khi chạy:

```powershell
mvn test
mvn clean package
```

Các test gồm:
- Checkout thành công, idempotency, cùng key nhưng khác body.
- Hai request checkout đồng thời cùng key chỉ tạo một order ID.
- Validate quantity, productId trùng và checkout key bắt buộc.
- Sai checkout key không thể đọc đơn.
- Catalog quote timeout: trả 503 và không tạo đơn PENDING.
- Reserve timeout: tạo PENDING, sau đó recovery đưa về CONFIRMED.
- Release timeout: tạo CANCEL_PENDING, sau đó recovery đưa về CANCELLED.
- C3 kiểm tra reservation ACTIVE/RELEASED; C2 PRICE_CHANGED chuyển REJECTED đúng mã lỗi.
- Hai checkout cùng key chạy đồng thời chỉ nhận một order ID.
- Hai đơn khác nhau tranh một sản phẩm tồn kho cuối: chỉ một đơn CONFIRMED.
- Hai yêu cầu hủy đồng thời hội tụ về CANCELLED; hủy lặp không hoàn kho hai lần.

Mock Catalog chỉ tự bật trong profile `dev` và `test`; mặc định các profile khác tắt. Các endpoint `/mock-catalog/internal/test/*` chỉ được bật trong Quarkus test profile, không bật ở dev/production.

## 7. Kết nối Catalog Service thật

Khi Catalog Service chạy tại `localhost:8081`, dừng app dev hiện tại và chạy:

```powershell
$env:CATALOG_BASE_URL = "http://localhost:8081"
$env:MOCK_CATALOG_ENABLED = "false"
$env:INTERNAL_API_KEY = "<cung-mot-secret-voi-Catalog>"
mvn quarkus:dev
```

Trong Docker Compose root của nhóm, Order container phải dùng URL nội bộ `http://catalog-service:8080`, không dùng `localhost:8081`. Hãy dùng chung chính xác `INTERNAL_API_KEY` với Catalog.

## 8. Trạng thái / giới hạn MVP

- Checkout key chỉ lưu SHA-256; không log/trả key gốc.
- Tạo đơn: C1 quote → T1 insert PENDING và items → C2 reserve → T2 CONFIRMED/REJECTED.
- Timeout/không rõ kết quả giữ PENDING; job chạy mỗi 60 giây, lấy tối đa 20 đơn cũ hơn 30 giây.
- Hủy: CONFIRMED → CANCEL_PENDING → C4 release → CANCELLED.
- Mock Catalog là stub local để học và test luồng. Trạng thái tồn kho mock ở RAM, không phải thay thế Catalog Service thật.
- Chỉ chạy một Order instance cho MVP recovery job như hướng dẫn. API key tĩnh phù hợp demo trong mạng tin cậy, không dùng public Internet.

## 9. Một lưu ý khi ghép nhóm

`quarkus.platform.version` hiện đặt mặc định `3.40.1.2`. Trước khi tích hợp, thống nhất phiên bản Quarkus 3.x cùng dòng với Catalog Service theo yêu cầu Contract chung. Nếu Catalog chọn phiên bản khác, đổi property này cho đồng nhất.


## 10. Đối chiếu GUIDE_Order_Service.md — các điểm đã hoàn thiện

- C1/C2/C3/C4 đều có REST Client; recovery gọi C3 trước để kiểm tra reservation ACTIVE/RELEASED, sau đó chỉ retry C2 khi chưa có reservation hoặc C3 tạm thời không truy cập được.
- Header checkout key được kiểm tra là UUID v4 canonical ở P1/P2/P3.
- Catalog response cho reserve/release phải trả đúng `orderId`.
- `RESERVATION_CONFLICT` và `RESERVATION_RELEASED` không bị coi là lỗi nghiệp vụ để reject đơn; đơn giữ trạng thái chờ xác minh.
- Request ID được trả qua `X-Request-Id`, đưa vào MDC để xuất hiện trong log.
- Recovery log có `orderId`, trạng thái và số lần thử trong vòng đời process.
- Có integration test cho quote timeout, reserve recovery, release recovery, C3 ACTIVE/RELEASED, PRICE_CHANGED, hai đơn tranh một sản phẩm cuối và hai yêu cầu hủy đồng thời.

Giới hạn vẫn giữ đúng MVP của tài liệu: một Order instance, checkout-key thay cho tài khoản, Mock Catalog chỉ phục vụ local/test, chưa có distributed transaction/Kafka/Saga.
