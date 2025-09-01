-- test.lua
-- 简单的测试脚本，验证KEYS参数传递是否正确
-- KEYS[1]: testKey
-- ARGV[1]: testValue

local key = KEYS[1]
local value = ARGV[1]

-- 设置测试值
redis.call('SET', key, value)

-- 读取并返回
local result = redis.call('GET', key)
return result

