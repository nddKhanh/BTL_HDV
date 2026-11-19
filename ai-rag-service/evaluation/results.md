# Báo cáo đánh giá chất lượng RAG (Evaluation Report)

Tài liệu này ghi nhận phương pháp, quy trình và kết quả đánh giá hệ thống trợ lý AI theo yêu cầu **F6 (GUIDE_AI_RAG_Service)**.

---

## 1. Phương pháp & Công cụ đánh giá

- **Bộ câu hỏi chuẩn:** 20 câu hỏi được định nghĩa tại [questions.md](file:///D:/University/5/HDV/BTL/ai-rag-service/evaluation/questions.md), bao gồm:
  - **8 câu FAQ / chính sách:** Đánh giá khả năng tìm kiếm và trả lời theo chính sách cửa hàng.
  - **7 câu sản phẩm:** Đánh giá khả năng truy xuất thông tin từ feed sản phẩm Catalog C5 (`Áo thun basic`, `Balo laptop`).
  - **5 câu ngoài phạm vi / từ chối:** Đánh giá cơ chế guardrail chống hallucination (hỏi giá realtime, tồn kho realtime, mật khẩu quản trị, mặt hàng không bán, tra cứu đơn hàng).
- **Công cụ chạy tự động:** [evaluate.py](file:///D:/University/5/HDV/BTL/ai-rag-service/evaluation/evaluate.py) hoặc `run-eval.ps1`.
- **Cấu hình mô hình:**
  - Embedding: `text-embedding-3-small` (1536 dimensions).
  - Chat: `gpt-4o-mini` (hoặc model OpenAI tương đương được cấu hình).
  - Ngưỡng tương đồng tối thiểu: `RAG_MIN_SCORE = 0.65`.
  - Top-k matches: `k = 4`.

---

## 2. Các chỉ số đo lường (Metrics)

| Chỉ số | Cách tính | Mục tiêu MVP |
|---|---|---|
| **Source Hit@4** | Tỷ lệ câu hỏi (1-15) mà top-4 chunks truy xuất chứa đúng nguồn (`FAQ:<id>` hoặc `PRODUCT:<id>`). | $\ge 85\%$ |
| **Refusal Accuracy** | Tỷ lệ câu hỏi (16-20) mà hệ thống từ chối thành công (`grounded=false`, không sinh nguồn giả). | $100\%$ |
| **Hallucinated Sources** | Số lần câu trả lời dẫn nguồn không tồn tại trong metadata của các chunk đã retrieval. | $0$ (Bảo đảm bằng code) |
| **Độ trễ p50 / p95** | Phân vị 50 và 95 của thời gian phản hồi toàn trình (Retrieval + LLM). | Đo lường thực tế |

---

## 3. Cách tiến hành đo lường

1. Khởi chạy database `knowledge_db` và Catalog C5 (service thật hoặc `python .\demo\mock_catalog.py`).
2. Khởi chạy `ai-rag-service`:
   ```powershell
   .\mvnw.cmd quarkus:dev
   ```
3. Nạp 10 FAQ mẫu và kích hoạt Reindex:
   ```powershell
   .\demo\load-faq.ps1
   Invoke-RestMethod -Method Post -Uri http://localhost:8083/api/admin/knowledge/reindex -Headers @{ 'X-Admin-Key'=$env:ADMIN_API_KEY }
   ```
4. Chạy script đánh giá tự động:
   ```powershell
   .\evaluation\run-eval.ps1
   ```

---

## 4. Kết quả nghiệm thu Unit Test nghiệp vụ

Trong môi trường kiểm thử tự động độc lập (không phụ thuộc vào mạng ngoài), toàn bộ logic cốt lõi của RAG đã được kiểm chứng qua **23 unit test cases**:
- **Guardrail từ chối ngay:** Các câu hỏi giá/tồn kho được nhận diện và từ chối tức thì mà không lãng phí token LLM.
- **Trích xuất nguồn:** Nguồn được trích xuất trực tiếp từ metadata `TextSegment`, loại bỏ hoàn toàn trùng lặp (`type:sourceId`).
- **Phân trang & Đồng bộ C5:** Dữ liệu sản phẩm ngừng bán bị xóa an toàn sau khi đã nhận đủ 100% dữ liệu từ feed.
