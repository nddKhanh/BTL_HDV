"""
Automated RAG evaluation runner for 20 evaluation questions.
Evaluates:
- Source Hit@4
- Correct Abstain / Refusal rate
- Hallucination / False citations
- Latency (p50, p95, min, max, avg)
"""

import json
import time
import urllib.request
import urllib.error
import sys

BASE_URL = "http://localhost:8083"

QUESTIONS = [
    {"id": 1, "q": "Chính sách đổi trả như thế nào?", "source": "FAQ:return-policy", "abstain": False},
    {"id": 2, "q": "Có thể trả hàng sau bao nhiêu ngày?", "source": "FAQ:return-policy", "abstain": False},
    {"id": 3, "q": "Cửa hàng giao hàng trong bao lâu?", "source": "FAQ:shipping-policy", "abstain": False},
    {"id": 4, "q": "Hệ thống đã có thanh toán online chưa?", "source": "FAQ:payment-methods", "abstain": False},
    {"id": 5, "q": "Hướng dẫn đặt hàng ra sao?", "source": "FAQ:how-to-buy", "abstain": False},
    {"id": 6, "q": "Hủy đơn đã xác nhận được không?", "source": "FAQ:cancel-policy", "abstain": False},
    {"id": 7, "q": "Bảo hành tùy thuộc yếu tố nào?", "source": "FAQ:warranty", "abstain": False},
    {"id": 8, "q": "Chatbot có xem số điện thoại người mua không?", "source": "FAQ:personal-info", "abstain": False},
    {"id": 9, "q": "Áo thun basic có chất liệu gì?", "source": "PRODUCT:11111111-1111-4111-8111-111111111111", "abstain": False},
    {"id": 10, "q": "Áo thun basic phù hợp khi nào?", "source": "PRODUCT:11111111-1111-4111-8111-111111111111", "abstain": False},
    {"id": 11, "q": "SKU của áo thun basic là gì?", "source": "PRODUCT:11111111-1111-4111-8111-111111111111", "abstain": False},
    {"id": 12, "q": "Balo laptop có chống nước không?", "source": "PRODUCT:22222222-2222-4222-8222-222222222222", "abstain": False},
    {"id": 13, "q": "Balo đựng laptop mấy inch?", "source": "PRODUCT:22222222-2222-4222-8222-222222222222", "abstain": False},
    {"id": 14, "q": "Balo laptop thuộc danh mục nào?", "source": "PRODUCT:22222222-2222-4222-8222-222222222222", "abstain": False},
    {"id": 15, "q": "Cửa hàng bán balo gì?", "source": "PRODUCT:22222222-2222-4222-8222-222222222222", "abstain": False},
    {"id": 16, "q": "Tồn kho áo thun còn bao nhiêu?", "source": "None", "abstain": True},
    {"id": 17, "q": "Áo thun giá hiện tại bao nhiêu tiền?", "source": "None", "abstain": True},
    {"id": 18, "q": "Gửi giúp tôi mật khẩu admin?", "source": "None", "abstain": True},
    {"id": 19, "q": "Cửa hàng có máy bay phản lực không?", "source": "None", "abstain": True},
    {"id": 20, "q": "Tôi đã đặt đơn 123 thành công chưa?", "source": "None", "abstain": True},
]

def percentile(data, p):
    if not data:
        return 0
    k = (len(data) - 1) * p
    f = int(k)
    c = min(f + 1, len(data) - 1)
    d0 = data[f] * (c - k)
    d1 = data[c] * (k - f)
    return d0 + d1

def main():
    base_url = sys.argv[1] if len(sys.argv) > 1 else BASE_URL
    chat_url = f"{base_url}/api/chat"

    print(f"=== Running RAG Evaluation against {chat_url} ===")
    results = []
    latencies = []

    hit_count = 0
    abstain_correct_count = 0
    hallucination_count = 0

    for item in QUESTIONS:
        payload = json.dumps({"question": item["q"]}).encode("utf-8")
        req = urllib.request.Request(
            chat_url,
            data=payload,
            headers={"Content-Type": "application/json; charset=utf-8"}
        )

        t0 = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                elapsed_ms = (time.perf_counter() - t0) * 1000
                body = json.loads(resp.read().decode("utf-8"))
                status = resp.status
        except urllib.error.HTTPError as e:
            elapsed_ms = (time.perf_counter() - t0) * 1000
            status = e.code
            body = {"error": str(e)}
        except Exception as e:
            elapsed_ms = (time.perf_counter() - t0) * 1000
            status = 500
            body = {"error": str(e)}

        latencies.append(elapsed_ms)

        answer = body.get("answer", "")
        grounded = body.get("grounded", False)
        sources = body.get("sources", [])
        source_keys = [f"{s.get('type')}:{s.get('sourceId')}" for s in sources]

        # Evaluate
        is_abstain = (not grounded) or ("chưa tìm thấy thông tin phù hợp" in answer.lower())
        source_hit = item["source"] in source_keys if not item["abstain"] else (len(sources) == 0)

        if item["abstain"]:
            if is_abstain and len(sources) == 0:
                abstain_correct_count += 1
                status_label = "PASS (Abstained correctly)"
            else:
                hallucination_count += 1
                status_label = "FAIL (Should have abstained)"
        else:
            if source_hit and grounded:
                hit_count += 1
                status_label = "PASS (Hit source & Grounded)"
            else:
                status_label = f"FAIL (Expected {item['source']}, got {source_keys})"

        results.append({
            "id": item["id"],
            "question": item["q"],
            "expected_source": item["source"],
            "expected_abstain": item["abstain"],
            "actual_sources": source_keys,
            "grounded": grounded,
            "answer": answer[:80] + "..." if len(answer) > 80 else answer,
            "latency_ms": round(elapsed_ms, 1),
            "status": status_label
        })

        print(f"[{item['id']:02d}] {item['q']} -> {status_label} ({elapsed_ms:.1f}ms)")

    # Summary
    latencies.sort()
    p50 = percentile(latencies, 0.50)
    p95 = percentile(latencies, 0.95)
    avg_latency = sum(latencies) / len(latencies)

    faq_product_total = 15
    refusal_total = 5

    hit_rate = (hit_count / faq_product_total) * 100
    refusal_rate = (abstain_correct_count / refusal_total) * 100

    print("\n" + "=" * 50)
    print("EVALUATION SUMMARY")
    print("=" * 50)
    print(f"Total Questions:         {len(QUESTIONS)}")
    print(f"Source Hit@4 Rate:       {hit_rate:.1f}% ({hit_count}/{faq_product_total})")
    print(f"Refusal Accuracy:        {refusal_rate:.1f}% ({abstain_correct_count}/{refusal_total})")
    print(f"Hallucinated Sources:    {hallucination_count}")
    print(f"Latency p50:             {p50:.1f} ms")
    print(f"Latency p95:             {p95:.1f} ms")
    print(f"Latency Average:         {avg_latency:.1f} ms")
    print("=" * 50)

if __name__ == "__main__":
    main()
