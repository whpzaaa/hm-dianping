package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Override
    public Result queryById(Long id) {
        //解决缓存穿透问题
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //利用互斥锁 解决缓存穿透
        //Shop shop = queryWithMutex(id);
       //逻辑过期解决缓存击穿问题
        Shop shop = cacheClient.queryWithLogicExpire(CACHE_SHOP_KEY, id, Shop.class, LOCK_SHOP_KEY, this::getById, 20L, TimeUnit.SECONDS);
        if (shop == null) {
            return  Result.fail("店铺信息不存在");
        }
        return Result.ok(shop);
    }
//    //利用逻辑过期解决缓存击穿问题
//    public Shop queryWithLogicExpire(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        //先从缓存中获取商铺信息
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //如果不存在数据(为空或为空值) 直接返回空
//        if (shopJson == null || shopJson.isEmpty()){
//            return null;
//        }
//        //如果缓存查询到数据
//        //将json数据数据反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        //jsonutill在反序列化时 将data数据反序列化为jsonobject类型
//        JSONObject jsonObject = (JSONObject) redisData.getData();
//        //再将jsonobject转为shop类型
//        Shop shop = JSONUtil.toBean(jsonObject, Shop.class);
//        //判断是否过期
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //未过期 直接返回数据
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            return shop;
//        }
//        //过期 进行缓存重建
//        //获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        //若获取锁失败  则直接返回过期数据
//        if (!isLock){
//            return shop;
//        }
//        //如果获取锁成功 先进行二次检查
//        //先从缓存中获取商铺信息
//        shopJson = stringRedisTemplate.opsForValue().get(key);
//        //如果不存在数据(为空或为空值) 直接返回空
//        if (shopJson == null || shopJson.isEmpty()){
//            return null;
//        }
//        //如果缓存查询到数据
//        //将json数据数据反序列化为对象
//        redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        //jsonutill在反序列化时 将data数据反序列化为jsonobject类型
//        jsonObject = (JSONObject) redisData.getData();
//        //再将jsonobject转为shop类型
//        shop = JSONUtil.toBean(jsonObject, Shop.class);
//        //判断是否过期
//        expireTime = redisData.getExpireTime();
//        //未过期 直接返回数据
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            return shop;
//        }
//        //若还是过期
//        //再开启一个线程进行缓存重建
//        CACHE_REBUILD_EXECUTOR.submit(()->{
//            try {
//                //重建缓存
//                saveShopToRedis(id,20L);
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            } finally {
//                //释放锁
//                unlock(lockKey);
//            }
//        });
//        return shop;
//    }
//    //缓存重建方法
//    public void saveShopToRedis(Long id , Long expireSeconds) throws InterruptedException {
//        Shop shop = getById(id);
//        //模拟长时间重建
//        Thread.sleep(200);
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
//    }
    //利用互斥锁解决缓存穿透
    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        //先从缓存中获取商铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //如果存在数据 且数据不为null和空字符串 则返回商铺数据
        if (shopJson != null && !shopJson.isEmpty()){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //解决缓存穿透问题
        //如果数据为空字符串 则返回空
        if (shopJson != null) {
            return null;
        }
        String lockKey = LOCK_SHOP_KEY + id;

        Shop shop;
        try {
            //重建缓存
            //1.获取互斥锁
            while (true) {
                boolean isLock = tryLock(lockKey);
                //2.判断获取是否成功
                //失败 休眠 并重新获取锁（递归或循环）
                if (!isLock) {
                    Thread.sleep(LOCK_SHOP_TTL);
                }
                //成功 跳出循环 查询数据
                if (isLock) {
                    //再从缓存中获取商铺信息 double check
                    shopJson = stringRedisTemplate.opsForValue().get(key);
                    //如果存在数据 且数据不为null和空字符串 则返回商铺数据
                    if (shopJson != null && !shopJson.isEmpty()){
                        shop = JSONUtil.toBean(shopJson, Shop.class);
                        return shop;
                    }
                    //解决缓存穿透问题
                    //如果数据为空字符串 则返回空
                    if (shopJson != null) {
                        return null;
                    }
                    break;
                }
            }
            //查询数据库
            shop = getById(id);
            //模拟重建时间长
            Thread.sleep(200);
            //如果数据库中无数据 则往redis中写入“”空字符串 并返回null
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key , "" , CACHE_NULL_TTL , TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //3.释放锁
            unlock(lockKey);
        }
        return shop;
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
//    //解决缓存穿透问题
//    public Shop queryWithPassThrough(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        //先从缓存中获取商铺信息
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //如果存在数据 且数据不为null和空字符串 则返回商铺数据
//        if (shopJson != null && !shopJson.isEmpty()){
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        //解决缓存穿透问题
//        //如果数据为空字符串 则返回错误信息
//        if (shopJson != null) {
//            return null;
//        }
//        //查询数据
//        Shop shop = getById(id);
//        //如果数据库中无数据 则往redis中写入“”空字符串
//        if (shop == null) {
//            stringRedisTemplate.opsForValue().set(key , "" , CACHE_NULL_TTL , TimeUnit.MINUTES);
//            return null;
//        }
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);
//        return shop;
//    }
}
