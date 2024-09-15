---
--- Created by 胡克岩.
--- DateTime: 2024/8/16 19:36
---

if(redis.call('get', KEYS[1]) == ARGV[1])  then
    return redis.call('del', KEYS[1])
end
return 0
