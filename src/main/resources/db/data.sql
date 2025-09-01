-- 初始化测试数据
USE seckill_test;
-- 清空现有数据
DELETE FROM orders;
DELETE FROM coupons;
-- 插入测试优惠券
INSERT INTO coupons (
    id,
    name,
    stock,
    total_stock,
    start_time,
    end_time
  )
VALUES (
    1,
    '限量iPhone15优惠券',
    100,
    100,
    '2024-01-01 10:00:00',
    '2024-12-31 22:00:00'
  ),
  (
    2,
    'MacBook Pro秒杀券',
    50,
    50,
    '2024-01-01 10:00:00',
    '2024-12-31 22:00:00'
  ),
  (
    3,
    '超值AirPods优惠券',
    200,
    200,
    '2024-01-01 10:00:00',
    '2024-12-31 22:00:00'
  ),
  (
    4,
    '限时iPad抢购券',
    80,
    80,
    '2024-01-01 10:00:00',
    '2024-12-31 22:00:00'
  ),
  (
    5,
    'Apple Watch秒杀券',
    150,
    150,
    '2024-01-01 10:00:00',
    '2024-12-31 22:00:00'
  );
-- 显示插入的数据
SELECT id,
  name,
  stock,
  total_stock,
  start_time,
  end_time
FROM coupons
ORDER BY id;