package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.SystemConstants.LOGIN_USER_TIME;
//拦截器一，不做拦截仅做刷新token有效期。
public class Interceptor01 implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public Interceptor01(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        if(StringUtil.isNullOrEmpty(token)){
            return true;
        }
        //根据token取出用户
        String strJson = stringRedisTemplate.opsForValue().get(LOGIN_USER_KEY + token);
        //没有登录
        if (StringUtil.isNullOrEmpty(strJson)) {
            return true;
        }
        //已经成功登录 获取用户
        User user = JSONUtil.toBean(strJson, User.class);
        UserHolder.saveUser(BeanUtil.copyProperties(user, UserDTO.class));
        //刷新有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TIME, TimeUnit.MINUTES);
        //System.out.println(UserHolder.getUser().toString());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
