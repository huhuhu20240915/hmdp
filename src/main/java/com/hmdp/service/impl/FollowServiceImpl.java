package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private FollowMapper followMapper;

    @Resource
    private IUserService userService;


    @Override
    public Result followOrNot(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, id.toString());
        if (BooleanUtil.isTrue(isMember)){
            return Result.ok(true);
        }else {
            return Result.ok(false);
        }

    }

    @Override
    public Result getFollow(Long followId, boolean judge) {
        Long userId = UserHolder.getUser().getId();
        if (judge){
            Follow follow = new Follow();
            follow.setFollowUserId(followId);
            //System.out.println(userId);
            follow.setUserId(userId);
            //System.out.println(follow);
            boolean save = save(follow);
            if (save){
                String key = "follow:" + userId;
                stringRedisTemplate.opsForSet().add(key,followId.toString());
            }
        }else {
            int i = followMapper.deleteByFollowIdAndUserId(followId, userId);
            if (i >= 0){
                String key = "follow:" + userId;
                stringRedisTemplate.opsForSet().remove(key,followId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result followCommon(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key1 = "follow:" + id;
        String key2 = "follow:" + userId;
        Set<String> common = stringRedisTemplate.opsForSet().intersect(key1,key2);

        /*System.out.println(key1);
        System.out.println(key2);
        System.out.println(common);*/

        if (common == null || common.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> collect = common.stream().map(Long::valueOf).collect(Collectors.toList());
        //System.out.println(collect);
        List<User> follows = userService.listByIds(collect);
        //System.out.println(follows);
        List<UserDTO> collect1 = follows.stream().map(u -> BeanUtil.copyProperties(u, UserDTO.class)).collect(Collectors.toList());
        //System.out.println(collect1);
        return Result.ok(collect1);
    }
}
