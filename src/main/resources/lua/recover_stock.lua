-- recover_stock.lua
-- 库存回滚Lua脚本，当订单处理失败时恢复Redis中的库存和用户购买记录
-- KEYS[1]: stockKey 库存键
-- KEYS[2]: orderKey 订单键
-- ARGV[1]: voucherId 优惠券ID
-- ARGV[2]: userId 用户ID

local voucherId = ARGV[1]
local userId = ARGV[2]

-- Redis key从KEYS数组获取
local stockKey = KEYS[1]
local orderKey = KEYS[2]

-- 1. 增加库存
redis.call('INCRBY', stockKey, 1)

-- 2. 减少用户购买记录
local bought = tonumber(redis.call('HGET', orderKey, userId))
if bought and bought > 0 then
    if bought == 1 then
        -- 如果购买数量为1，直接删除该字段
        redis.call('HDEL', orderKey, userId)
    else
        -- 减少购买数量
        redis.call('HSET', orderKey, userId, bought - 1)
    end
end

-- 3. 清除时间记录  
local timeKey = 'seckill:time:' .. voucherId
redis.call('HDEL', timeKey, userId)

-- 4. 记录回滚操作（用于监控和调试）
redis.call('LPUSH', 'seckill:rollback:log', 
    string.format('{"voucherId":"%s","userId":"%s","time":"%s"}', 
    voucherId, userId, redis.call('TIME')[1]))

-- 限制回滚日志长度，只保留最近1000条
redis.call('LTRIM', 'seckill:rollback:log', 0, 999)

return 0 -- 成功
