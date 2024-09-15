package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    ShopServiceImpl shopService;

    @Test
    public void test01(){
        /*long epochSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        System.out.println(epochSecond);*/

        LocalDateTime time = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
        long epochSecond = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(epochSecond);

    }

}
