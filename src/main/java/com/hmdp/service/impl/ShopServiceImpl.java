package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopPlus;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import static com.hmdp.utils.SystemConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    StringRedisTemplate stringRedisTemplate;


    private ExecutorService executorService = new ThreadPoolExecutor(2,4,5,TimeUnit.SECONDS,new ArrayBlockingQueue<>(5));



    //利用缓存逻辑时间解决缓存穿透
    @Override
    public Result getShopById(Long id) {
        String JsonStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isBlank(JsonStr)){
            return Result.fail("店铺信息不存在");
        }
        System.out.println("1");
        ShopPlus shopPlus = JSONUtil.toBean(JsonStr, ShopPlus.class);
        if (shopPlus.getLocalDateTime().isAfter(LocalDateTime.now())) {
            //未过期
            System.out.println(shopPlus.getLocalDateTime().toString());
            System.out.println(LocalDateTime.now().toString());
            JSONObject data = (JSONObject)shopPlus.getData();
            Shop shop = JSONUtil.toBean(data, Shop.class);
            return Result.ok(shop);
        }
        System.out.println("2");
        //以过期
        boolean lock = getLock(id);
        if (!lock){
            JSONObject data = (JSONObject)shopPlus.getData();
            Shop shop = JSONUtil.toBean(data, Shop.class);
            return Result.ok(shop);
        }
        //开启独立线程
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                Shop shop = getById(id);
                if (shop == null){
                    releaseLock(id);
                    return;
                }
                ShopPlus shopPlus1 = new ShopPlus();
                shopPlus1.setData(shop);
                shopPlus1.setLocalDateTime(LocalDateTime.now().plusSeconds(60 * 60 * 24 * 30));
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shopPlus1));
                releaseLock(id);
            }
        });
        JSONObject data = (JSONObject)shopPlus.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        return Result.ok(shop);
    }


    //主动更新保持数据一致性
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        if (shop.getId() == null){
            return Result.fail("商铺id不能为空");
        }
        LocalDateTime time1 = LocalDateTime.now().plusSeconds(60 * 60 * 24 *30);
        ShopPlus shopPlus = new ShopPlus();
        shopPlus.setLocalDateTime(time1);
        shopPlus.setData(shop);
        String jsonStr = JSONUtil.toJsonStr(shopPlus);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + shop.getId(), jsonStr);
        return Result.ok();
    }


    //缓存""来解决缓存穿透
    public Result getShopByIdInCaseOfNull(Long id) {
        String JsonStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(JsonStr)){
            Shop shop = JSONUtil.toBean(JsonStr, Shop.class);
            return Result.ok(shop);
        }
        if ("".equals(JsonStr)){
            return Result.fail("店铺信息不存在1");
        }

        Shop shop = getById(id);
        if (shop == null){
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",2, TimeUnit.SECONDS);
            return Result.fail("店铺信息不存在");
        }
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }


    //互斥锁解决缓存击穿
    public Result getShopByIdByLock(Long id){
        String JsonStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(JsonStr)){
            Shop shop = JSONUtil.toBean(JsonStr, Shop.class);
            return Result.ok(shop);
        }
        if ("".equals(JsonStr)){
            return Result.fail("店铺信息不存在1");
        }
        //获取锁
        Shop shop = null;
        try {
            boolean lock = getLock(id);
            if (!lock){
                Thread.sleep(500);
                getShopByIdInCaseOfNull(id);
            }
            //获取成功，二次判断
            String s = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if (StrUtil.isNotBlank(s)){
                Shop shop1 = JSONUtil.toBean(JsonStr, Shop.class);
                return Result.ok(shop1);
            }
            shop = getById(id);
            if (shop == null){
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"", CACHE_SHOP_TIME, TimeUnit.SECONDS);
                return Result.fail("店铺信息不存在");
            }
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            releaseLock(id);
        }
        return Result.ok(shop);
    }



    //获取锁
    public boolean getLock(Long key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(CACHE_SHOP_LOCK_KEY + key, "1");
        return BooleanUtil.isTrue(b);
    }

    //释放锁
    public boolean releaseLock(Long key){
        Boolean b = stringRedisTemplate.delete(CACHE_SHOP_LOCK_KEY + key);
        return BooleanUtil.isTrue(b);
    }

    //提前缓存带逻辑过期时间的商铺信息
    public void sentShopWithLogicTime(Long id){
        Shop shop = query().eq("id", id).one();
        if (shop == null){
            System.out.println("商铺id错误");;
        }
        ShopPlus shopPlus = new ShopPlus();
        shopPlus.setData(shop);
        shopPlus.setLocalDateTime(LocalDateTime.now().plusSeconds(60));
        String jsonStr = JSONUtil.toJsonStr(shopPlus);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr);
    }

}
