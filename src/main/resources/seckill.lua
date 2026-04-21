-- 订单号
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. userId
-- 库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end
-- 用户是否下单
if (tonumber(redis.call('sismember', orderKey)) == 1) then
    return 2
end

redis.call('incrby', 'stockKey', -1)
redis.call('sadd', 'orderKey', userId)
-- 发送消息
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0

