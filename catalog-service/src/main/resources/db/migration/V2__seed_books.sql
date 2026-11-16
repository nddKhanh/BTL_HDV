INSERT INTO categories (id, name) VALUES
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1', 'Công nghệ thông tin'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2', 'Kinh tế & Quản trị'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3', 'Văn học & Tiểu thuyết');

INSERT INTO products (id, category_id, sku, name, description, price_vnd, available_stock, active) VALUES
  (
    '11111111-1111-4111-8111-111111111111',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1',
    'BOOK-IT-001',
    'Thiết Kế Hệ Thống Microservices Với Quarkus',
    'Sách hướng dẫn kiến trúc Microservices thực chiến sử dụng Java 21, Quarkus, PostgreSQL và Docker.',
    250000,
    25,
    TRUE
  ),
  (
    '11111111-1111-4111-8111-111111111112',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2',
    'BOOK-BIZ-001',
    'Tư Duy Nhanh Và Chậm',
    'Tác phẩm kinh điển về kinh tế học hành vi và cơ chế ra quyết định của con người.',
    189000,
    40,
    TRUE
  ),
  (
    '11111111-1111-4111-8111-111111111113',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3',
    'BOOK-LIT-001',
    'Nhà Giả Kim',
    'Tiểu thuyết truyền cảm hứng về hành trình theo đuổi ước mơ và khám phá bản thân.',
    95000,
    60,
    TRUE
  );
