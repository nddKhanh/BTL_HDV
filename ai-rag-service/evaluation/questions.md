# Bộ câu hỏi đánh giá MVP (điền kết quả thực tế sau khi chạy)

Dữ liệu kỳ vọng là 10 FAQ giả lập tại `demo/faq-sample.json` và 2 sản phẩm từ `demo/mock_catalog.py`.
Đây là kiểm thử dự kiến, KHÔNG phải kết quả đã đo.

| # | Câu hỏi | Expected source | Kỳ vọng từ chối? |
|---|---|---|---|
| 1 | Chính sách đổi trả như thế nào? | FAQ:return-policy | Không |
| 2 | Có thể trả hàng sau bao nhiêu ngày? | FAQ:return-policy | Không |
| 3 | Cửa hàng giao hàng trong bao lâu? | FAQ:shipping-policy | Không |
| 4 | Hệ thống đã có thanh toán online chưa? | FAQ:payment-methods | Không |
| 5 | Hướng dẫn đặt hàng ra sao? | FAQ:how-to-buy | Không |
| 6 | Hủy đơn đã xác nhận được không? | FAQ:cancel-policy | Không |
| 7 | Bảo hành tùy thuộc yếu tố nào? | FAQ:warranty | Không |
| 8 | Chatbot có xem số điện thoại người mua không? | FAQ:personal-info | Không |
| 9 | Áo thun basic có chất liệu gì? | PRODUCT:11111111-1111-4111-8111-111111111111 | Không |
| 10 | Áo thun basic phù hợp khi nào? | PRODUCT:11111111-1111-4111-8111-111111111111 | Không |
| 11 | SKU của áo thun basic là gì? | PRODUCT:11111111-1111-4111-8111-111111111111 | Không |
| 12 | Balo laptop có chống nước không? | PRODUCT:22222222-2222-4222-8222-222222222222 | Không |
| 13 | Balo đựng laptop mấy inch? | PRODUCT:22222222-2222-4222-8222-222222222222 | Không |
| 14 | Balo laptop thuộc danh mục nào? | PRODUCT:22222222-2222-4222-8222-222222222222 | Không |
| 15 | Cửa hàng bán balo gì? | PRODUCT:22222222-2222-4222-8222-222222222222 | Không |
| 16 | Tồn kho áo thun còn bao nhiêu? | None | Có |
| 17 | Áo thun giá hiện tại bao nhiêu tiền? | None | Có |
| 18 | Gửi giúp tôi mật khẩu admin? | None | Có |
| 19 | Cửa hàng có máy bay phản lực không? | None | Có |
| 20 | Tôi đã đặt đơn 123 thành công chưa? | None | Có |

Đo `source hit@4`, tỷ lệ từ chối, tỷ lệ dẫn nguồn phù hợp, độ trễ p50/p95. Chỉ ghi số sau khi chạy.
