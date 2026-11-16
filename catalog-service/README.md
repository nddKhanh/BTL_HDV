# catalog-service — Quarkus + PostgreSQL 16 + Flyway

Service quản lý danh mục, sản phẩm (sách), báo giá và giữ/hoàn tồn kho theo **GUIDE_Catalog_Service (Contract V1.0)**.

## 1. Yêu cầu

- Java **21** (Temurin), Maven Wrapper (`mvnw` / `mvnw.cmd`), Docker Desktop.
- Database riêng `catalog_db` (PostgreSQL 16) ánh xạ cổng host `5433`; ứng dụng chạy ở cổng `8081` khi phát triển cục bộ.

## 2. Khởi chạy Database & Dev Mode (Windows PowerShell)

```powershell
cd catalog-service

# 1. Tạo file .env từ mẫu (không commit .env lên Git)
Copy-Item .env.example .env

# 2. Khởi chạy PostgreSQL 16 (catalog_db) ở cổng 127.0.0.1:5433
docker compose -f compose.catalog-db.yaml up -d

# 3. Chạy ứng dụng ở chế độ Quarkus Dev (tự động chạy Flyway V1 & V2)
.\mvnw.cmd quarkus:dev
```

- **Swagger UI:** `http://localhost:8081/q/swagger-ui/`
- **Health check:** `http://localhost:8081/q/health`

## 3. Chạy kiểm thử & Đóng gói Docker

```powershell
# Chạy toàn bộ unit/integration & concurrency test
.\mvnw.cmd test

# Đóng gói JVM fast-jar và build Docker image
.\mvnw.cmd clean package -DskipTests
docker build -t catalog-service:local .
```

## 4. Danh sách API & Contract

Mọi phản hồi đều trả thêm header `X-Request-Id`. Các lỗi trả về theo cấu trúc thống nhất:
`{"error":{"code":"...","message":"...","requestId":"..."}}`.

### Public API (không yêu cầu API key)
| Method & Path | Mô tả |
|---|---|
| `GET /api/categories` | Danh sách danh mục sách (`name ASC, id ASC`) |
| `GET /api/categories/{id}` | Chi tiết một danh mục (`404 CATEGORY_NOT_FOUND` nếu không tồn tại) |
| `GET /api/products?page=0&size=20&query=...&categoryId=...` | Tìm kiếm & lọc sách đang bán (`active=true`, `size <= 100`, `name ASC, id ASC`) |
| `GET /api/products/{id}` | Chi tiết một đầu sách đang bán (`404 PRODUCT_NOT_FOUND` nếu không thấy hoặc `active=false`) |

### Admin API (Header bắt buộc: `X-Admin-Key`)
| Method & Path | Mô tả |
|---|---|
| `POST /api/admin/categories` | Tạo danh mục mới (`201 Created`, trùng tên trả `409 CATEGORY_EXISTS`) |
| `PUT /api/admin/categories/{id}` | Cập nhật tên danh mục |
| `DELETE /api/admin/categories/{id}` | Xóa danh mục chưa có sách tham chiếu (`204`, nếu đang dùng trả `409 CATEGORY_IN_USE`) |
| `POST /api/admin/products` | Tạo sách mới (`201 Created`, trùng SKU trả `409 SKU_EXISTS`) |
| `PUT /api/admin/products/{id}` | Cập nhật thông tin & giá sách (không thay đổi `availableStock` qua `PUT`) |
| `PATCH /api/admin/products/{id}/stock` | Cập nhật tồn kho `availableStock` (dùng khóa dòng `PESSIMISTIC_WRITE`) |
| `DELETE /api/admin/products/{id}` | Ngừng bán mềm (`active=false`, trả `204 No Content`) |

### Internal API — Contract C1–C5 (Header bắt buộc: `X-Internal-Key`)
| Contract | Method & Path | Mô tả |
|---|---|---|
| **C1** | `POST /internal/catalog/quote` | Báo giá cho Order Service (1–20 dòng, `quantity` 1–99, không trùng `productId`, không trừ kho) |
| **C2** | `POST /internal/stock/reservations` | Giữ tồn kho idempotent theo `orderId` + SHA-256 payload (`201` mới / `200` gọi lặp, khóa `SELECT ... FOR UPDATE` theo `productId ASC`) |
| **C3** | `GET /internal/stock/reservations/{orderId}` | Tra cứu trạng thái giữ kho (`ACTIVE` / `RELEASED` hoặc `404 RESERVATION_NOT_FOUND`) |
| **C4** | `POST /internal/stock/reservations/{orderId}/release` | Hoàn tồn kho idempotent (`200 RELEASED`, gọi lặp chỉ cộng kho đúng 1 lần) |
| **C5** | `GET /internal/ai/products?offset=0&limit=50` | Xuất tri thức sản phẩm `active=true` cho AI RAG (`limit <= 100`, không lộ giá/tồn kho) |
