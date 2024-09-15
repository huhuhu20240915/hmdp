package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.hmdp.utils.iLock;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    ISeckillVoucherService iSeckillVoucherService;

    @Resource
    iLock iLock;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    RedissonClient redissonClient;

    private static final ExecutorService Seckill_single_excutor = Executors.newSingleThreadExecutor();

    public static DefaultRedisScript<Long> script;

    private static IVoucherOrderService iVoucherOrderService;

    static  {
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("secKill.lua"));
        script.setResultType(Long.class);
    }

    @Override
    public Result getOrder(Long voucherId) {
        iVoucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
        //通过lua脚本，判断有无购买资格，若有，直接将订单id、用户id、优惠券id传入消息队列
        Long userId = UserHolder.getUser().getId();
        RedisIdWorker redisIdWorker = new RedisIdWorker(stringRedisTemplate);
        long orderId = redisIdWorker.nextId("voucherOrder:" + voucherId);
        Long result = stringRedisTemplate.execute(script, Collections.emptyList(), String.valueOf(voucherId),
                String.valueOf(userId), String.valueOf(orderId));
        if (result == 0){
            return Result.ok(orderId);
        }else {
            String str = result == 1 ? "库存不足" : "不允许重复下单";
            return Result.fail(str);
        }
    }


    //提交异步任务
    @PostConstruct
    private void init(){
        Seckill_single_excutor.submit(new handle());
    }

    //创建异步线程下单
    private class handle implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    //System.out.println("01");
                    //从消息队列读取消息
                    List<MapRecord<String, Object, Object>> result = stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed()));
                    //整理取出数据
                    if (result.isEmpty() || result == null){
                        continue;
                    }
                    MapRecord<String, Object, Object> entries = result.get(0);
                    Map<Object, Object> value = entries.getValue();
                    VoucherOrder order = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), CopyOptions.create().ignoreError());
                    //创建订单
                    iVoucherOrderService.createOrder(order);
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", entries.getId());
                } catch (Exception e) {
                    while (true){
                        try {
                            List<MapRecord<String, Object, Object>> result = stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1"),
                                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                                    StreamOffset.create("stream.orders", ReadOffset.from("0")));
                            //整理取出数据
                            if (result.isEmpty() || result == null){
                                break;
                            }
                            MapRecord<String, Object, Object> entries = result.get(0);
                            Map<Object, Object> value = entries.getValue();
                            VoucherOrder order = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), CopyOptions.create().ignoreError());
                            //创建订单
                            iVoucherOrderService.createOrder(order);
                            stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", entries.getId());
                        } catch (Exception ex) {
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException exc) {
                                throw new RuntimeException(exc);
                            }
                        }
                    }
                }
            }
        }
    }


    @Transactional
    @Override
    public void createOrder(VoucherOrder order) {
        RLock lock = redissonClient.getLock("order");
        //双从判断
        System.out.println("01");
        try {
            if (lock.tryLock()){
                save(order);
                iSeckillVoucherService.update().setSql("stock = stock - 1")
                        .eq("voucher_id", order.getVoucherId())
                        .ge("stock",0).update();
            }
        } finally {
            lock.unlock();
        }

    }




    /*@Override
    public Result getOrder(Long voucherId) {
        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
        if (seckillVoucher == null){
            return Result.fail("没有该优惠券");
        }
        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime()) || LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())){
            return Result.fail("未到活动时间");
        }
        if (seckillVoucher.getStock() <= 0){
            return Result.fail("优惠券库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //一人一单
        Integer count = query().eq("user_id", userId).eq("voucher_id", seckillVoucher.getVoucherId()).count();
        if (count > 0){
            return Result.fail("一人只能下一单哦");
        }
        //分布式锁
        //获取redission锁对象
        RLock lock = redissonClient.getLock("order");
        if (*//*iLock.tryLock("order"*//* lock.tryLock())) {
            try {
                IVoucherOrderService iVoucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
                return iVoucherOrderService.createOrder(seckillVoucher);
            } catch (IllegalStateException e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
        }else {
            return Result.fail("一人只能下一单");
        }
    }


    //创建优惠券订单
    @Transactional
    public Result createOrder(SeckillVoucher voucher) {
        RedisIdWorker redisIdWorker = new RedisIdWorker(stringRedisTemplate);
        long orderId = redisIdWorker.nextId("voucherOrder:" + voucher.getVoucherId());
        VoucherOrder order = new VoucherOrder();
        order.setUserId(UserHolder.getUser().getId());
        order.setId(orderId);
        order.setVoucherId(voucher.getVoucherId());
        //乐观锁
        iSeckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucher.getVoucherId()).gt(
                "stock",0
        ).update();
        //VoucherOrder order = createOrder(seckillVoucher);
        save(order);
        return Result.ok(order.getId());
    }*/
}
