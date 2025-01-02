package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }
    //解决缓存穿透问题
    public <R,ID> R queryWithPassThrough(
            String keyPrefix , ID id , Class<R> type , Function<ID , R> dbFallback,Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //先从缓存中获取商铺信息
        String Json = stringRedisTemplate.opsForValue().get(key);
        //如果存在数据 且数据不为null和空字符串 则返回商铺数据
        if (Json != null && !Json.isEmpty()){
            R r = JSONUtil.toBean(Json,type);
            return r;
        }
        //解决缓存穿透问题
        //如果数据为空字符串 则返回错误信息
        if (Json != null) {
            return null;
        }
        //查询数据
        R r = dbFallback.apply(id);
        //如果数据库中无数据 则往redis中写入“”空字符串
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key , "" , CACHE_NULL_TTL , TimeUnit.MINUTES);
            return null;
        }
        this.set(key,r,time,unit);
        return r;
    }
    //利用逻辑过期解决缓存击穿问题
    public <R , ID> R queryWithLogicExpire(String keyPrefix, ID id , Class<R> type , String lockKeyPrefix,Function<ID , R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //先从缓存中获取商铺信息
        String Json = stringRedisTemplate.opsForValue().get(key);
        //如果不存在数据(为空或为空值) 直接返回空
        if (Json == null || Json.isEmpty()){
            return null;
        }
        //如果缓存查询到数据
        //将json数据数据反序列化为对象
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        //jsonutill在反序列化时 将data数据反序列化为jsonobject类型
        JSONObject jsonObject = (JSONObject) redisData.getData();
        //再将jsonobject转为shop类型
        R r= JSONUtil.toBean(jsonObject, type);
        //判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        //未过期 直接返回数据
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        //过期 进行缓存重建
        //获取互斥锁
        String lockKey =lockKeyPrefix + id;
        boolean isLock = tryLock(lockKey);
        //若获取锁失败  则直接返回过期数据
        if (!isLock){
            return r;
        }
        //如果获取锁成功 先进行二次检查
        //先从缓存中获取商铺信息
        Json = stringRedisTemplate.opsForValue().get(key);
        //如果不存在数据(为空或为空值) 直接返回空
        if (Json == null || Json.isEmpty()){
            return null;
        }
        //如果缓存查询到数据
        //将json数据数据反序列化为对象
        redisData = JSONUtil.toBean(Json, RedisData.class);
        //jsonutill在反序列化时 将data数据反序列化为jsonobject类型
        jsonObject = (JSONObject) redisData.getData();
        //再将jsonobject转为shop类型
        r = JSONUtil.toBean(jsonObject, type);
        //判断是否过期
        expireTime = redisData.getExpireTime();
        //未过期 直接返回数据
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        //若还是过期
        //再开启一个线程进行缓存重建
        CACHE_REBUILD_EXECUTOR.submit(()->{
            try {
                //重建缓存
                R r1 = dbFallback.apply(id);
                this.setWithLogicalExpire(key,r1,time,unit);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                //释放锁
                unlock(lockKey);
            }
        });
        return r;
    }
    //获取锁 如果set成功 说明是第一个获取成功
    private boolean tryLock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }
    //释放锁 将数据写入缓存之后释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
