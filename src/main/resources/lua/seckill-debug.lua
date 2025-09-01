-- seckill-debug.lua
-- 调试版本的秒杀脚本
-- KEYS[1]: stockKey
-- KEYS[2]: orderKey  
-- ARGV[1]: voucherId
-- ARGV[2]: userId  
-- ARGV[3]: limit

local voucherId = ARGV[1]
local userId = ARGV[2]
local limit = tonumber(ARGV[3])

local stockKey = KEYS[1]
local orderKey = KEYS[2]

-- 记录所有调试信息到一个专门的key
local debugKey = 'seckill:debug:' .. voucherId

-- 清理旧的调试信息
redis.call('DEL', debugKey)

-- 记录脚本开始执行
redis.call('HSET', debugKey, 'script_start', 'true')
redis.call('HSET', debugKey, 'stockKey', stockKey)
redis.call('HSET', debugKey, 'orderKey', orderKey)

-- 1. 读取库存
local stockValue = redis.call('GET', stockKey)
redis.call('HSET', debugKey, 'raw_stock_value', tostring(stockValue or 'nil'))

local stock = tonumber(stockValue)
redis.call('HSET', debugKey, 'parsed_stock', tostring(stock or 'nil'))

-- 如果库存为空或不足，返回错误
if stock == nil then
    redis.call('HSET', debugKey, 'error', 'stock_is_nil')
    return 1
end

if stock <= 0 then
    redis.call('HSET', debugKey, 'error', 'stock_insufficient')
    return 1  
end

-- 2. 检查用户购买记录
local bought = tonumber(redis.call('HGET', orderKey, userId))
if bought == nil then
    bought = 0
end

redis.call('HSET', debugKey, 'user_bought', tostring(bought))

-- 3. 检查限购
if bought >= limit then
    redis.call('HSET', debugKey, 'error', 'exceed_limit')
    return 2
end

-- 4. 扣减库存并更新购买记录
redis.call('DECRBY', stockKey, 1)
redis.call('HSET', orderKey, userId, bought + 1)

-- 5. 记录成功信息
redis.call('HSET', debugKey, 'result', 'success')
redis.call('HSET', debugKey, 'new_stock', redis.call('GET', stockKey))

return 0

