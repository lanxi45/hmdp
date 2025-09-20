package com.hmdp.service.impl;

import cn.hutool.cache.Cache;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import org.apache.tomcat.jni.Local;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = cacheClient
//                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);


        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop = cacheClient
                .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        //返回
        return Result.ok(shop);
    }

    /*private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);*/

    /*public Shop queryWithLogicalExpire(Long id){
        //1.从Redis中查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中
        if(StrUtil.isBlank(shopJson)){
            //3，如果命中，返回商铺信息
            return null;
        }

        //4.命中，需要先把json反序列化为对象
        // 将JSON字符串转换为RedisData对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        // 从RedisData中提取店铺数据并转换为Shop对象
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        // 从Redis数据中获取店铺信息的JSON对象
        JSONObject shop2Json = (JSONObject) redisData.getData();
        // 将JSON对象转换为Shop实体类
        Shop shop = JSONUtil.toBean(shop2Json, Shop.class);

        // 获取数据的过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断缓存是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1. 未过期，返回商铺信息
            return shop;
        }
        //5.2. 已过期，需要缓存重建
        //6.缓存重建
        //6.1 尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2判断是否获取锁成功
        if(isLock){
            //6.3. 获取锁，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存
                    this.saveShop2Redis(id, LOCK_SHOP_TTL);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
                // 直接返回商铺信息
                return shop;
            });
        }
        //6.4. 未获取，返回商铺信息
        return shop;
    }*/

    /*public Shop queryWithMutex(Long id){
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
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断名声的是否是空值
        if(shopJson != null){
            //返回一个错误信息
            return null;
        }
        //4. 实现缓存重建
        //4.1获取互斥锁
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //4.2判断是否获取成功
            if(!isLock){
                //4.3失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //这里需要做一个doubleCheck
            //4.4成功，根据id查询数据库
            shop = getById(id);
            //模拟重建的延迟
            Thread.sleep(200);
            //5.不存在，返回404错误
            if (shop == null){
                //将空值写入Redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //6.存在，将商铺数据写入Redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //7.释放互斥锁
            unlock(lockKey);
        }
        //8.返回
        return shop;
    }*/

    /*public Shop queryWithPassThrough(Long id){
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
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断名声的是否是空值
        if(shopJson != null){
            //返回一个错误信息
            return null;
        }

        //4.未命中，根据id从数据库中查询
        Shop shop = getById(id);//mybatisplus
        //5.不存在，返回404错误
        if (shop == null){
            //将空值写入Redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //6.存在，将商铺数据写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7.返回
        return shop;
    }*/

    /*private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        //直接返回，Java的自动拆箱可能会返回空指针，所以这里我们使用BooleanUtil.isTrue自动判断
        //因为这个不是基本类型的boolean，这个Boolean是boolean的包装类
        //网络问题或键不存在但Redis未响应，setIfAbsent可能会返回null
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }*/

    /*public void saveShop2Redis(Long id, Long expirSeconds) throws InterruptedException {
        //1. 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expirSeconds));
        //3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }*/

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
