# ai-rag-service — MVP Quarkus + LangChain4j + PostgreSQL/pgvector

> Bản code khởi đầu theo GUIDE_AI_RAG_Service (Contract V1.0). Một AI instance,
> reindex đồng bộ theo yêu cầu, **không** triển khai đa instance/streaming/realtime sync.
>
> **Trạng thái xác minh:** đã kiểm tra cấu trúc và cú pháp file tĩnh; chưa chạy `mvn test`
> hay kiểm thử tích hợp OpenAI/PostgreSQL trong môi trường tạo mã (không có Maven/Docker).
> Chỉ coi hệ thống là chạy được khi hoàn thành các bước smoke test bên dưới trên máy của bạn.

## 1. Yêu cầu

- Windows 10 PowerShell, Java **21**, Maven 3.9+, Docker Desktop.
- Một **OpenAI API key** có quyền truy cập `gpt-4o-mini` (hoặc model cấu hình khác)
  và `text-embedding-3-small`; đây **không** phải khóa nhận từ gói ChatGPT Plus.
- Catalog C5 tại `http://localhost:8081` hoặc mock có sẵn tại `demo/mock_catalog.py`.
- Database riêng `knowledge_db` chạy ở cổng host `5435`; AI service cổng `8083`.

## 2. Bắt đầu trong PowerShell

```powershell
# Chạy trong thư mục ai-rag-service-mvp
java -version
mvn -version
Copy-Item .env.example .env
notepad .env               # sửa OpenAI key và tất cả replace_...
. .\demo\load-env.ps1      # dấu chấm + khoảng trắng ở đầu rất quan trọng
docker compose -f compose.ai-db.yaml up -d
```

Nếu Catalog thật **chưa xong**, mở **terminal khác** tại thư mục project:

```powershell
python .\demo\mock_catalog.py
```

Sau đó quay lại terminal có biến môi trường:

```powershell
mvn quarkus:dev
```

Kiểm tra `http://localhost:8083/q/swagger-ui/` và `http://localhost:8083/q/health`.

Nếu Catalog đã có, **không chạy mock**; để `CATALOG_BASE_URL=http://localhost:8081`
trỏ đến service thật. C5 chỉ gửi sản phẩm active và không gửi giá/tồn kho.

## 3. Chạy thử end-to-end

Trong **terminal PowerShell thứ ba**, load cùng `.env` rồi nạp 10 FAQ mẫu:

```powershell
. .\demo\load-env.ps1
.\demo\load-faq.ps1
.\demo\try-chat.ps1
```

`try-chat.ps1` làm đúng thứ tự: `POST reindex` rồi `POST /api/chat` 4 lần.
Kỳ vọng reindex trả `READY`, `documentCount = 12`, `chunkCount >= 12` nếu
mock C5 có 2 sản phẩm và 10 FAQ; kết quả trả lời còn phụ thuộc model/threshold.
Tất cả các nội dung chính sách trong `demo/faq-sample.json` **chỉ là ví dụ**
cho cửa hàng giả lập, cần thay bằng chính sách thật nếu sử dụng ở nơi khác.

### Gọi API thủ công

```powershell
$headers = @{ 'X-Admin-Key' = $env:ADMIN_API_KEY }
Invoke-RestMethod -Method Post -Uri http://localhost:8083/api/admin/knowledge/reindex -Headers $headers -ContentType 'application/json' -Body '{}'

$payload = @{ question='Áo thun basic làm bằng chất liệu gì?' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8083/api/chat -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($payload))
```

Ví dụ response đúng A1:

```json
{
  "answer": "Áo thun basic làm từ cotton 100%.",
  "grounded": true,
  "sources": [
    {"type":"PRODUCT","sourceId":"11111111-1111-4111-8111-111111111111","title":"Áo thun basic"}
  ]
}
```

**Đây là mẫu response kỳ vọng, không phải kết quả chạy đã xác minh.**

## 4. Contract đã triển khai

| API | Chức năng | Header |
|---|---|---|
| `POST /api/admin/knowledge/documents` | A2: upsert FAQ, 201/200 SAVED | `X-Admin-Key` |
| `POST /api/admin/knowledge/reindex` | A3: fetch C5, embed, full rebuild, 200 READY / 503 REINDEX_FAILED | `X-Admin-Key` |
| `POST /api/chat` | A1: semantic top-4, LLM + sources | không cần |
| outbound `GET /internal/ai/products` | C5: import active products, pagination | `X-Internal-Key` |

Điều kiện ở bản MVP:
- Upsert FAQ **không tự động reindex**.
- Reindex lấy đủ các trang C5 rồi mới sync PRODUCT documents; xóa sản phẩm vắng mặt
  chỉ sau khi fetch xong. Lỗi trang sau không xóa dữ liệu sản phẩm cũ.
- Chunks có metadata `sourceType`, `sourceId`, `title`, `documentId`, `chunkIndex`.
- Tính embedding từng batch trước khi xóa index cũ. Nếu lỗi trong lúc ghi vector,
  trạng thái `FAILED`, chat trả 503 cho đến khi reindex lại thành công.
- Extension quản lý bảng `embeddings`; code chỉ làm `DELETE FROM embeddings` (tên bảng
  cố định và cấu hình rõ ràng), không phụ thuộc tên các cột nội bộ.
- Một process AI; không hỗ trợ reindex đồng thời trên nhiều instance.
- `RAG_MIN_SCORE` mặc định 0.65 là giá trị **thử nghiệm**, không phải số đo.
- Không dùng vector để trả giá/tồn kho hiện tại. Không liên lạc Order Service.
- `grounded=true` nghĩa là có ngữ cảnh trích xuất được, **không phải** bảo đảm
  toàn bộ lời LLM đều chính xác; hãy đối chiếu các nguồn khi đánh giá.

## 5. Kiểm thử và ghép vào root Compose

```powershell
mvn test
mvn clean package -DskipTests
```

Đưa `ai-rag-service-mvp/` vào root repo với tên `ai-rag-service/`, giữ Dockerfile
của project. Trong root compose GUIDE, `build: ./ai-rag-service`, `PORT=8080`,
`AI_JDBC_URL=jdbc:postgresql://ai-db:5432/knowledge_db`,
`CATALOG_BASE_URL=http://catalog-service:8080` và map `8083:8080`.
**Không chạy đồng thời** `compose.ai-db.yaml` và `ai-db` trong root Compose trên
cùng host port 5435.

### Smoke test bắt buộc trước khi chốt MVP

1. Gọi FAQ create hai lần cùng `sourceId`: lần đầu 201, lần sau 200;
   sửa content và reindex để chatbot thấy nội dung mới.
2. Chạy reindex hai lần: số chunk không tăng vô hạn; trả `READY`.
3. Hỏi về sản phẩm từ C5: trả `grounded=true` và `sources.type=PRODUCT` hợp lệ.
4. Hỏi về FAQ: nguồn là `FAQ`; hỏi giá hoặc tồn kho: không cam kết dữ liệu realtime.
5. Tắt mock Catalog và reindex: `503 REINDEX_FAILED`; khởi động lại và reindex:
   `READY` trở lại. Không xóa PRODUCT documents chỉ vì fetch thất bại.
6. Tắt OpenAI API: `503 AI_UNAVAILABLE` khi chat; không được báo câu trả lời thành công.
7. Restart Quarkus + DB (không xóa volume): FAQ, vector vẫn tồn tại, chat vẫn dùng được
   nếu lần reindex trước đã hoàn thành.

## 6. Nguồn kỹ thuật

- https://docs.quarkiverse.io/quarkus-langchain4j/dev/rag-pgvector-store.html
- https://docs.quarkiverse.io/quarkus-langchain4j/dev/openai-chat-model.html
- https://docs.quarkiverse.io/quarkus-langchain4j/dev/quickstart-rag.html
- https://quarkus.io/guides/rest-client

Pinned Quarkus Platform 3.40.1.1 + cùng phiên bản LangChain4j platform BOM; Java 21.
