package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import io.netty.util.internal.StringUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;


@Component
public class RedisLock implements iLock{
    @Resource
    StringRedisTemplate stringRedisTemplate;

    public String prefix = "lock:";

    public static DefaultRedisScript<Long> script;

    static  {
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("unlock.lua"));
        script.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(String key) {
        long id = Thread.currentThread().getId();
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(prefix + key,
                id + "", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }


    //利用lua脚本保证原子性
    @Override
    public void releaseLock(String key) {
        stringRedisTemplate.execute(script,
                Collections.singletonList(prefix + key),
                String.valueOf(Thread.currentThread().getId()));
    }

   /* @Override
    public void releaseLock(String key) {
        String s = stringRedisTemplate.opsForValue().get(prefix + key);
        if (String.valueOf(Thread.currentThread().getId()).equals(s)){
            stringRedisTemplate.delete(prefix + key);
        }
    }*/
}
