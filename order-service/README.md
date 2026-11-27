# order-service — Quarkus + PostgreSQL 16 + Flyway

Service quản lý guest checkout và điều phối vòng đời đơn hàng.
Order Service gọi Catalog để báo giá và giữ/hoàn tồn kho, gọi Payment, Shipping và Notification khi thanh toán hoặc hủy đơn.

## 1. Chạy

Cách chạy khuyến nghị là chạy cả hệ thống bằng Docker Compose ở thư mục gốc, xem [README](../README.md).

Chạy riêng ở chế độ dev (cần `order_db` ở cổng `5434` và các service phụ thuộc đang chạy):

```powershell
$env:ORDER_DB_PASSWORD = "..."
$env:INTERNAL_API_KEY = "..."
..\catalog-service\mvnw.cmd -f pom.xml quarkus:dev
```

Ứng dụng chạy ở cổng `8082`.
Địa chỉ các service phụ thuộc đặt bằng `CATALOG_BASE_URL`, `PAYMENT_BASE_URL`, `SHIPPING_BASE_URL` và `NOTIFICATION_BASE_URL`.

## 2. API

| API | Mô tả |
|---|---|
| `POST /api/orders` | Tạo đơn từ email, địa chỉ giao hàng và 1–20 dòng hàng; trả về `checkoutKey` một lần duy nhất. |
| `GET /api/orders/{id}` | Xem đơn, cần header `X-Checkout-Key`. |
| `POST /api/orders/{id}/pay` | Thanh toán đơn `CONFIRMED`; thành công thì đơn sang `PAID`, tạo vận đơn và ghi thông báo. |
| `POST /api/orders/{id}/cancel` | Hủy đơn `CONFIRMED` hoặc `PAID`; hoàn tồn kho, đơn đã thanh toán thì hủy vận đơn và hoàn tiền. |

Trạng thái đơn: `PENDING` → `CONFIRMED` → `PAID`, hoặc `CANCELLED`.
Kịch bản end-to-end nằm ở `scripts/e2e/commerce-flow.sh`.
