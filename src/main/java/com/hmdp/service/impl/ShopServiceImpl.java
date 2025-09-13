package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //1.从Redis中查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中
        if(StrUtil.isNotBlank(shopJson)){
            //3，如果命中，返回商铺信息
            //将从Redis中获取的商铺信息（JSON字符串）反序列化为Shop对象，以便后续返回给前端。
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            //这里之所以将json串变成对象，然后controller再将其变成json串，
            // 是因为，这个过程会加上一个content-type给前端，告诉他是json串
            return Result.ok(shop);
        }
        //判断名声的是否是空值
        if(shopJson != null){
            //返回一个错误信息
            return Result.fail("店铺信息不存在");
        }

        //4.未命中，根据id从数据库中查询
        Shop shop = getById(id);//mybatisplus
        //5.不存在，返回404错误
        if (shop == null){
            //将空值写入Redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return Result.fail("店铺不存在");
        }
        //6.存在，将商铺数据写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7.返回
        return Result.ok(shop);
    }

    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        //1. 更新数据库
        updateById(shop);
        //2. 删除
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return null;
    }
}
