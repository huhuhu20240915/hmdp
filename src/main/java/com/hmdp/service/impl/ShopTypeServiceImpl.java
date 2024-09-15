package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import static com.hmdp.utils.SystemConstants.CACHE_SHOP_TYPE_KEY;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getShopTypeList() {
        //从redis中取
        String shopTypeJsonStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        if (StringUtil.isNullOrEmpty(shopTypeJsonStr)){
            //从数据库取并写入缓存
            List<ShopType> typeList = query().orderByAsc("sort").list();
            if (typeList == null || typeList.isEmpty()) {
                return Result.fail("店铺类型查询失败");
            }
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(typeList));
            return Result.ok(typeList);
        }
        java.util.List<ShopType> shopTypesList = JSONUtil.toList(shopTypeJsonStr, ShopType.class);
        return Result.ok(shopTypesList);
    }




}
