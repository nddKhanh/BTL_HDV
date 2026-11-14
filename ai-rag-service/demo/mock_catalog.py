"""Local-only C5 stub. Use this only when Catalog Service is not available."""
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs
import json

PRODUCTS = [
    {"id": "11111111-1111-4111-8111-111111111111", "sku": "TS-001",
     "name": "Áo thun basic", "description": "Áo cotton 100%, cổ tròn, thích hợp mặc hằng ngày.",
     "categoryName": "Thời trang"},
    {"id": "22222222-2222-4222-8222-222222222222", "sku": "BP-001",
     "name": "Balo laptop 15 inch", "description": "Balo chống nước nhẹ, có ngăn đựng laptop 15 inch.",
     "categoryName": "Phụ kiện"}
]

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path != "/internal/ai/products":
            return self.respond(404, {"error": {"code": "NOT_FOUND"}})
        if not self.headers.get("X-Internal-Key"):
            return self.respond(401, {"error": {"code": "UNAUTHORIZED"}})
        qs = parse_qs(parsed.query)
        try:
            offset = int(qs.get("offset", ["0"])[0])
            limit = int(qs.get("limit", ["50"])[0])
            if offset < 0 or not (1 <= limit <= 100):
                raise ValueError
        except ValueError:
            return self.respond(422, {"error": {"code": "INVALID_PAGE"}})
        data = sorted(PRODUCTS, key=lambda p: p["id"])
        end = offset + limit
        self.respond(200, {"items": data[offset:end], "nextOffset": end if end < len(data) else None})

    def respond(self, status, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

if __name__ == "__main__":
    print("Mock C5 available at http://localhost:8081/internal/ai/products")
    HTTPServer(("127.0.0.1", 8081), Handler).serve_forever()
