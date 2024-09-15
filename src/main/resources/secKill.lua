---
--- Created by 胡克岩.
--- DateTime: 2024/8/24 17:34
---
--优惠券id
local voucherId = ARGV[1]
--用户id
local userId = ARGV[2]
--订单号
local orderId = ARGV[3]

--数据kety
--库存key
local stockKey = 'seckill:stock:' .. voucherId
--订单key
local orderKey = 'seckill:order:' .. voucherId

if (tonumber(redis.call('get',stockKey)) <= 0) then
    return 1
end

if (redis.call('sismember',orderKey,userId) == 1) then
     return 2
end

redis.call('incrby', stockKey, -1)

redis.call('sadd', orderKey, userId)

redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)

return 0
