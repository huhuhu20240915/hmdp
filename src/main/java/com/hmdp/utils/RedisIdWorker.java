package com.hmdp.utils;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


@Component
public class RedisIdWorker {

    StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long time0 = 946684800L;

    public long nextId(String key){

        //生成时间戳
        long now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long time = now - time0;
        //生成序列号
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd");
        String day = LocalDateTime.now().format(dateTimeFormatter);
        long increment = stringRedisTemplate.opsForValue().increment("icr:" + key + ":" + day);
        return time << 32 | increment;

    }




}
