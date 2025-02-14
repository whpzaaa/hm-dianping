package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoLocation;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //如果没有传入经纬度坐标 则直接分页查询
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
       //若传入坐标 则进行坐标查询
        //1.分析分页参数
        int begin = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //2.去redis中查询数据
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = stringRedisTemplate.opsForGeo().search(key,
                //圆心
                GeoReference.fromCoordinate(new Point(x, y)),
                //距离
                new Distance(5000),
                //其他条件 返回距离和查到第几条
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        //如果数据为空 则直接返回
        if (geoResults == null) {
            return Result.ok(Collections.emptyList());
        }
        //获取结果集合
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> results = geoResults.getContent();
        //存放店铺id
        List<Long> ids = new ArrayList<>(results.size());
        //存放店铺id和距离
        Map<String , Distance> map = new HashMap<>(results.size());
        //如果begin大于结果集合的长度 则直接返回 否则会空指针
        if (begin >= results.size()) {
            return Result.ok(Collections.emptyList());
        }
        //跳过begin个数据 将id放入ids集合中 将距离也放入map中
        results.stream().skip(begin).forEach(geoLocationGeoResult -> {
            String idStr = geoLocationGeoResult.getContent().getName();
            Distance distance = geoLocationGeoResult.getDistance();
            ids.add(Long.valueOf(idStr));
            map.put(idStr,distance);
        });
        //3.通过店铺id查询数据库
        String idsStr = StrUtil.join(",", ids);
        List<Shop> list = query().in("id", ids).last("order by field (id," + idsStr + ")").list();
        //4.返回店铺数据（含距离）
        //遍历集合通过店铺id从map中获取店铺的距离并赋值
        for (Shop shop : list) {
            shop.setDistance(map.get(shop.getId().toString()).getValue());
        }
        return Result.ok(list);
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
        Shop shop;
        String key = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;
        try {
            //重建缓存
            //1.获取互斥锁
            while (true) {
                //先从缓存中获取商铺信息
                String shopJson = stringRedisTemplate.opsForValue().get(key);
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
