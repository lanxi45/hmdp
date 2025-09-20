package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPreFix, ID id, Class<R> type, Function<ID, R> dbFallback,Long time, TimeUnit unit){
        String key = keyPreFix + id;
        //1.从Redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中
        if(StrUtil.isNotBlank(json)){
            //3，如果命中，返回信息
            //将从Redis中获取的信息（JSON字符串）反序列化，以便后续返回给前端。
            //这里之所以将json串变成对象，然后controller再将其变成json串，
            // 是因为，这个过程会加上一个content-type给前端，告诉他是json串
            return JSONUtil.toBean(json, type);
        }
        //判断名声的是否是空值
        if(json != null){
            //返回一个错误信息
            return null;
        }

        //4.未命中，根据id从数据库中查询
        R r =dbFallback.apply(id);
        //5.不存在，返回404错误
        if (r == null){
            //将空值写入Redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //6.存在，将商铺数据写入Redis
        this.set(key, r, time, unit);
        //7.返回
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R queryWithLogicalExpire(
            String keyPreFix, ID id, Class<R> type, Function<ID, R> dbFallback,Long time, TimeUnit unit){
        //1.从Redis中查询商铺缓存
        String key = keyPreFix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中
        if(StrUtil.isBlank(json)){
            //3，如果命中，返回商铺信息
            return null;
        }

        //4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断缓存是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1. 未过期，返回商铺信息
            return r;
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
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //写入Redis
                    this.setWithLogicalExpire(key, r1, time, unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //6.4. 未获取，返回商铺信息
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        //直接返回，Java的自动拆箱可能会返回空指针，所以这里我们使用BooleanUtil.isTrue自动判断
        //因为这个不是基本类型的boolean，这个Boolean是boolean的包装类
        //网络问题或键不存在但Redis未响应，setIfAbsent可能会返回null
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

}
