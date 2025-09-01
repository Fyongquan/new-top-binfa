-- seckill.lua
-- 秒杀核心Lua脚本，确保库存检查和扣减的原子性
-- KEYS[1]: stockKey 库存键
-- KEYS[2]: orderKey 订单键  
-- ARGV[1]: voucherId 优惠券ID
-- ARGV[2]: userId 用户ID  
-- ARGV[3]: limit 限购数量

local voucherId = ARGV[1]
local userId = ARGV[2]
local limit = tonumber(ARGV[3])

-- Redis key从KEYS数组获取
local stockKey = KEYS[1]
local orderKey = KEYS[2]

-- 创建调试键
local debugKey = 'seckill:debug:' .. voucherId

-- 详细调试信息：记录传入的参数和键
redis.call('HSET', debugKey, 'stockKey', tostring(stockKey or 'nil'))
redis.call('HSET', debugKey, 'orderKey', tostring(orderKey or 'nil'))
redis.call('HSET', debugKey, 'voucherId', tostring(voucherId or 'nil'))
redis.call('HSET', debugKey, 'userId', tostring(userId or 'nil'))

-- 检查键是否存在
local keyExists = redis.call('EXISTS', stockKey)
redis.call('HSET', debugKey, 'key_exists', tostring(keyExists))

-- 1. 检查库存是否充足
local stockValue = redis.call('GET', stockKey)
local stock = tonumber(stockValue)

-- 调试信息：记录读取到的原始值和转换后的值
redis.call('HSET', debugKey, 'raw_stock', tostring(stockValue or 'nil'))
redis.call('HSET', debugKey, 'parsed_stock', tostring(stock or 'nil'))

-- 增加错误调试
if stockValue == nil then
    redis.call('HSET', debugKey, 'error', 'stockValue_is_null')
    return 1 -- 库存不足
end

if stock == nil then
    redis.call('HSET', debugKey, 'error', 'stock_parse_failed_stockValue_was_' .. tostring(stockValue))
    return 1 -- 库存解析失败
end

if stock <= 0 then
    redis.call('HSET', debugKey, 'error', 'stock_insufficient_value_' .. tostring(stock))
    return 1 -- 库存不足
end

-- 2. 获取用户已购买数量
local bought = tonumber(redis.call('HGET', orderKey, userId))
if bought == nil then
    bought = 0
end

-- 3. 检查是否超过限购
if bought >= limit then
    return 2 -- 超过个人限购
end

-- 4. 扣减库存并更新购买记录
redis.call('DECRBY', stockKey, 1)
redis.call('HSET', orderKey, userId, bought + 1)

-- 5. 记录操作时间（用于监控）
redis.call('HSET', 'seckill:time:' .. voucherId, userId, redis.call('TIME')[1])

return 0 -- 成功
