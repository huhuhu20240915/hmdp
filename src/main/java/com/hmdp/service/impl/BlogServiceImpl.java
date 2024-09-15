package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Resource
    private IBlogService blogService;


    @Override
    public Result updateLikeByID(Long id) {
        String key = "blog:like" + id;
        String userId = UserHolder.getUser().getId().toString();
        Double ismember = stringRedisTemplate.opsForZSet().score(key,userId);
        if (ismember != null){
            boolean judge1 = update().setSql("Liked = Liked - 1").eq("id", id).update();
            if (BooleanUtil.isTrue(judge1)){
                stringRedisTemplate.opsForZSet().remove(key,userId);
            }
        }else {
            boolean judge2 = update().setSql("Liked = Liked + 1").eq("id", id).update();
            if (BooleanUtil.isTrue(judge2)){
                stringRedisTemplate.opsForZSet().add(key,userId,System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            judgeIsLiked(blog.getId(), blog);
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        });

        return Result.ok(records);
    }

    @Override
    public Result queryByid(Long id) {
        // 根据用户查询
        Blog blog = getById(id);
        //查询islike属性
        judgeIsLiked(id, blog);
        // 查询用户
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        return Result.ok(blog);
    }

    @Override
    public Result queryLikesLink(Long id) {
        String key = "blog:like" + id;
        //从sorted中取出前五个元素
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, 5);
        //得到用户id
        if (range == null || range.isEmpty()){
            return Result.ok();
        }
        List<Long> collect = range.stream().map(Long::valueOf).collect(Collectors.toList());
        String s = StrUtil.join(",", collect);
        //System.out.println(collect);
        //查询用户信息
        List<User> blogs = userService.query().in("id",collect).last("ORDER BY FIELD (id,"+ s +")").list();
        //转换为userdto
        List<UserDTO> collect1 = blogs.stream().map(blog -> BeanUtil.copyProperties(blog, UserDTO.class)).collect(Collectors.toList());
        /*List<Object> UserDTOList = blogs.stream().map(blog ->
                BeanUtil.copyProperties(blog, UserDTO.class)
        ).collect(Collectors.toList());*/
        //System.out.println(UserDTOList);
        return Result.ok(collect1);
    }

    @Override
    public Result getBlogByFollow(Long maxtime, Integer offset) {
        //查询当前用户id
        Long userId = UserHolder.getUser().getId();
        String key = "receiveBox:" + userId;
        //滚动查询信箱
        /**参数依次为：
         * key
         * Score最小值
         * Score最大值
         * 偏移量 首次查询为0 为了应对Score重复问题
         * count 查询数量
         */
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
                .opsForZSet().reverseRangeByScoreWithScores(key,0,maxtime,offset,2);
        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        //计算下一次查询偏移量
        Long mintime = 0L;
        Integer offset1 = 1;
        ArrayList<Blog> list = new ArrayList(2);
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            Long blogId = Long.valueOf(Objects.requireNonNull(typedTuple.getValue()));
            Blog blog = query().eq("id", blogId).one();
            Long thistime = typedTuple.getScore().longValue();
            if (thistime == mintime){
                offset1++;
            }else {
                offset1 = 1;
            }
            //查询是否点赞以及注入相关用户
            queryByid(blogId);
            list.add(blog);
        }
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(list);
        scrollResult.setOffset(offset1);
        scrollResult.setMinTime(mintime);
        return Result.ok(scrollResult);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        save(blog);
        //查询所有粉丝，并推荐到其收件箱
        Long userId = UserHolder.getUser().getId();
        List<Follow> fansList = followService.query().eq("follow_user_id", userId).list();
        List<Long> fansIdList = fansList.stream().map(Follow::getUserId).collect(Collectors.toList());
        if (fansIdList != null || fansIdList.isEmpty())
            for (Long fanId : fansIdList) {
                String key = "receiveBox:" + fanId;
                stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
            }
        // 返回id
        return Result.ok(blog.getId());
    }

    private void judgeIsLiked(Long id, Blog blog) {
        String key = "blog:like" + id;
        if (UserHolder.getUser() == null){
            return;
        }
        String userIdNow = UserHolder.getUser().getId().toString();
        Double ismember = stringRedisTemplate.opsForZSet().score(key,userIdNow);
        if (ismember != null){
            blog.setIsLike(true);
        }else {
            blog.setIsLike(false);
        }
    }
}
