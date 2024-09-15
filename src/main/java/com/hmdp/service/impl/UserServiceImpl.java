package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sentCode(String phone) {
        //判断手机格式
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if (phoneInvalid){
            return Result.fail("手机号码格式错误！");
        }
        //格式正确
        String code = RandomUtil.randomNumbers(6);
        //存入redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code);
        stringRedisTemplate.expire(LOGIN_CODE_KEY + phone,LOGIN_CODE_TIME, TimeUnit.MINUTES);
        //模拟发送验证码
        System.out.println(code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //判断手机格式
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(loginForm.getPhone());
        if (phoneInvalid){
            return Result.fail("手机号码格式错误！");
        }
        //格式正确
        //获取验证码
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        if (StringUtil.isNullOrEmpty(code) || StringUtil.isNullOrEmpty(loginForm.getCode())){
            return Result.fail("验证码错误！");
        }
        //校验
        if (!code.equals(loginForm.getCode())){
            return Result.fail("验证码错误！");
        }
        //查询用户
        User user;
        user = query().eq("phone", loginForm.getPhone()).one();
        if (user == null){
            user = createUser(loginForm.getPhone());
        }
        //缓存用户到redis
        String token = UUID.randomUUID().toString();
        String jsonStr = JSONUtil.toJsonStr(user);
        stringRedisTemplate.opsForValue().set(LOGIN_USER_KEY + token,jsonStr, LOGIN_USER_TIME, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result getCurUser() {
        return Result.ok(UserHolder.getUser());
    }


    //创建新用户
    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
