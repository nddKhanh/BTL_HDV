# Báo cáo rà soát hoàn thiện Order Service

## Căn cứ

Đối chiếu trực tiếp `GUIDE_Order_Service.md`, Contract V1.0, với source `order-service` trong gói bàn giao.

## Các chỉnh sửa trong bản này

1. Bắt buộc `ORDER_DB_PASSWORD` và `INTERNAL_API_KEY` từ môi trường ở profile mặc định; không còn fallback secret trong `application.properties`.
2. Chỉ bật Mock Catalog tự động trong profile `dev` và `test`; profile mặc định tắt. Fault injection và điều chỉnh tồn kho chỉ hoạt động trong `%test`.
3. Bổ sung xử lý C3 `404 RESERVATION_NOT_FOUND` như trạng thái chưa có reservation, không xem là lỗi nghiệp vụ để reject. Recovery kiểm tra C3 trước, xác nhận `ACTIVE`, giữ cảnh báo nếu `RELEASED` bất thường, và fallback sang C2 idempotent khi chưa có reservation hoặc C3 tạm thời không sẵn sàng.
4. Bổ sung mock/test cho `PRICE_CHANGED` và cấu hình tồn kho để mô phỏng tranh chấp đơn hàng.
5. Bổ sung kiểm thử C3 ACTIVE/RELEASED, PRICE_CHANGED, hai checkout cùng key đồng thời, hai đơn tranh một đơn vị tồn kho, và hai yêu cầu hủy đồng thời.
6. Compose DB yêu cầu mật khẩu được cấp qua biến môi trường; `.env` được loại khỏi Git.
7. Cập nhật README và hướng dẫn biến môi trường.

## Kiểm tra tĩnh đã thực hiện

- `pom.xml` parse XML thành công.
- Kiểm tra cân bằng dấu ngoặc trên 35 tệp Java thành công.
- Kiểm tra các cấu hình bắt buộc: env-only secrets, profile mock, test controls.
- Kiểm tra sự hiện diện của P1/P2/P3, C1/C2/C3/C4, scheduler 60 giây và các trạng thái state machine.
- Kiểm tra nội dung `.gitignore` có loại `.env`.

## Kiểm tra chưa thể thực hiện trong môi trường tạo gói

Môi trường hiện tại có JDK 21 nhưng không cài Maven và Docker. Vì vậy `mvn test`, `mvn clean package`, `docker compose config` và kiểm thử PostgreSQL/HTTP thực tế chưa chạy được. Không được xem các test mới là đã pass cho tới khi chạy trên máy có Maven 3.9+ và PostgreSQL 16/Docker.

## Lệnh nghiệm thu trên Windows PowerShell

```powershell
$env:ORDER_DB_PASSWORD = "orders_dev_password"
$env:INTERNAL_API_KEY = "replace-with-a-long-random-secret"
docker compose -f compose.db.yaml up -d
mvn test
mvn clean package
```

Nếu hai lệnh Maven đều thành công, tiếp tục chạy `mvn quarkus:dev`, kiểm tra `/q/health`, `/q/swagger-ui`, rồi tích hợp Catalog thật tại `http://localhost:8081` và chạy kịch bản end-to-end theo mục J của GUIDE.

## Phạm vi còn phụ thuộc tích hợp nhóm

Order Service chỉ có thể chứng minh contract thực tế C1-C4 khi Catalog Service thật đã chạy và được kiểm thử cùng. Mock Catalog trong source chỉ hỗ trợ phát triển độc lập, không thay thế kiểm thử liên service.
