# ai-rag-service — MVP Quarkus + LangChain4j + PostgreSQL/pgvector

> Triển khai theo **GUIDE_AI_RAG_Service (Contract V1.0)** và **DINH_HUONG_THIET_KE_HE_THONG_MVP**.
> Một AI instance độc lập, reindex đồng bộ theo yêu cầu, retrieval top-k = 4, sinh câu trả lời kèm trích dẫn nguồn thực tế từ FAQ và dữ liệu sản phẩm Catalog C5.
>
> **Trạng thái:** Toàn bộ mã nguồn, security filter, health check và bộ **23 unit tests** đã vượt qua kiểm thử tự động (`BUILD SUCCESS`). Hỗ trợ chạy kiểm thử và đóng gói độc lập qua Maven Wrapper (`mvnw.cmd`).

---

## 1. Yêu cầu môi trường

- Windows 10/11 PowerShell, Java **21** (Temurin/Oracle JDK 21), Docker Desktop (khi chạy container/compose).
- Maven Wrapper đã tích hợp sẵn trong repo (`mvnw` / `mvnw.cmd`), **không cần cài đặt Maven toàn cục**.
- Gemini API key có quyền truy cập model chat (mặc định `gemini-2.5-flash`) và model embedding (`text-embedding-004`, 768 chiều).
- Catalog C5 tại `http://localhost:8081` hoặc stub có sẵn tại `demo/mock_catalog.py`.
- Database riêng `knowledge_db` chạy ở cổng host `5435`; AI service chạy ở cổng `8083`.

---

## 2. Hướng dẫn khởi chạy cục bộ (Local Development)

### Bước 1: Chuẩn bị biến môi trường
```powershell
cd ai-rag-service
Copy-Item .env.example .env
# Chỉnh sửa GEMINI_API_KEY và các mật khẩu trong file .env
notepad .env

# Load biến môi trường vào session PowerShell hiện tại (lưu ý dấu chấm và khoảng trắng)
. .\demo\load-env.ps1
```

### Bước 2: Khởi chạy database `knowledge_db` (pgvector)
```powershell
docker compose -f compose.ai-db.yaml up -d
```

### Bước 3: Chuẩn bị Catalog C5
- Nếu **chưa chạy `catalog-service` thật**: mở một terminal riêng và chạy mock catalog:
  ```powershell
  python .\demo\mock_catalog.py
  ```
- Nếu **đã có `catalog-service` thật**: đặt `CATALOG_BASE_URL=http://localhost:8081` trong file `.env`.

### Bước 4: Chạy ứng dụng Quarkus Dev Mode
```powershell
.\mvnw.cmd quarkus:dev
```

Sau khi khởi động thành công:
- **Swagger UI:** `http://localhost:8083/q/swagger-ui/`
- **Health check tổng thể:** `http://localhost:8083/q/health`
- **Ping endpoint:** `http://localhost:8083/api/ping`

---

## 3. Chạy thử nghiệm End-to-End & Đánh giá RAG

Trong một cửa sổ PowerShell mới, load biến môi trường rồi nạp dữ liệu FAQ và chạy thử:

```powershell
. .\demo\load-env.ps1

# 1. Nạp 10 tài liệu FAQ mẫu (A2 API)
.\demo\load-faq.ps1

# 2. Chạy reindex và thử chat 4 câu hỏi cơ bản
.\demo\try-chat.ps1

# 3. Chạy bộ đánh giá tự động 20 câu hỏi (F6 Requirement)
.\evaluation\run-eval.ps1
```

---

## 4. Danh sách API Contracts đã triển khai

Tất cả các response trả về đều có header `X-Request-Id`. Lỗi chuẩn hóa theo schema:
`{"error": {"code": "...", "message": "...", "requestId": "..."}}`.

| Method & Path | Contract | Xác thực | Mô tả chức năng |
|---|---|---|---|
| `POST /api/chat` | **A1** | Public | Trợ lý RAG: tìm kiếm vector top-4, tạo ngữ cảnh, gọi LLM, trích xuất metadata nguồn (`FAQ` / `PRODUCT`). Tự động từ chối nếu không có ngữ cảnh hoặc câu hỏi về giá/tồn kho realtime. |
| `POST /api/admin/knowledge/documents` | **A2** | `X-Admin-Key` | Upsert tài liệu FAQ/chính sách vào `knowledge_documents`. Trả `201 SAVED` khi tạo mới, `200 SAVED` khi cập nhật. Kiểm tra chặt độ dài và chống script injection. |
| `POST /api/admin/knowledge/reindex` | **A3** | `X-Admin-Key` | Đồng bộ toàn diện: gọi Catalog C5 lấy sản phẩm active, tách chunks 500-800 ký tự, tính vector embedding, làm mới bảng `embeddings` pgvector. Trả `200 READY` hoặc `503 REINDEX_FAILED`. |
| Outbound: `GET /internal/ai/products` | **C5** | `X-Internal-Key` | Lấy dữ liệu sản phẩm phân trang từ Catalog Service (`offset`, `limit <= 100`). Chỉ nhận sản phẩm active, không đọc giá và tồn kho. |
| `GET /api/ping` | - | Public | Liveness ping nhanh trả về `Hello from AI RAG Service`. |
| `GET /q/health` | - | Public | MicroProfile Health / SmallRye Health liveness & readiness check. |

---

## 5. Kiểm thử & Đóng gói

### Chạy Unit Test suite
Service đi kèm bộ **23 unit tests** độc lập, kiểm tra toàn diện nghiệp vụ mà không cần bật database hay Gemini API key thật:
```powershell
.\mvnw.cmd test
```

Các test case bao gồm:
1. `KnowledgeServiceTest` (7 tests): Lưu mới, cập nhật FAQ, validate các trường bắt buộc, chặn thẻ `<script>`.
2. `ProductSyncServiceTest` (6 tests): Xử lý phân trang C5, chống lặp vô hạn `nextOffset`, xử lý trùng lặp sản phẩm, xóa sản phẩm ngừng bán sau khi fetch đủ trang.
3. `AnswerServiceTest` (7 tests): Kiểm tra validate câu hỏi, kiểm tra trạng thái index READY (trả 503 nếu chưa sẵn sàng), **từ chối ngay câu hỏi giá/tồn kho realtime mà không gọi LLM**, loại bỏ nguồn trùng lặp, xử lý lỗi mô hình.
4. `ChunkServiceTest` (2 tests): Kiểm tra logic chia văn bản, chồng lấp (overlap) và metadata.
5. `HealthCheckResourceTest` (1 test): Kiểm tra endpoint ping.

### Đóng gói ứng dụng & Docker Build
```powershell
# Đóng gói fast-jar
.\mvnw.cmd clean package -DskipTests

# Build Docker image nội bộ
docker build -t ai-rag-service:local .
```

---

## 6. Đánh giá chất lượng RAG (Evaluation)

Hệ thống cung cấp script đánh giá tự động tại `evaluation/evaluate.py`:
- **Bộ câu hỏi:** 20 câu hỏi tại `evaluation/questions.md` (8 câu FAQ, 7 câu mô tả sản phẩm, 5 câu ngoài phạm vi/yêu cầu từ chối).
- **Các chỉ số đo lường:**
  - **Source Hit@4:** Tỷ lệ tìm được đúng nguồn tài liệu trong top 4 chunks.
  - **Refusal Accuracy:** Tỷ lệ từ chối chính xác đối với các câu hỏi ngoài phạm vi, câu hỏi mật khẩu, và câu hỏi đòi giá/tồn kho thời gian thực.
  - **Độ trễ phản hồi:** Đo độ trễ thực tế p50, p95 và trung bình (ms).
  - **Hallucinated Sources:** Phát hiện bất kỳ trường hợp LLM tự bịa nguồn ngoài metadata thực tế.

Chạy đánh giá:
```powershell
python .\evaluation\evaluate.py http://localhost:8083
```
