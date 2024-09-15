package com.hmdp.config;

import com.hmdp.utils.Interceptor01;
import com.hmdp.utils.Interceptor02;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MyConfig implements WebMvcConfigurer {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new Interceptor01(stringRedisTemplate)).addPathPatterns("/**").order(0);

        /*registry.addInterceptor(new Interceptor02()).excludePathPatterns("/user/login","/user/code","/user/sign",
                "/shop/**","/voucher/**","/shop-type/**","/upload/**","/blog/**").order(1);*/
    }
}
