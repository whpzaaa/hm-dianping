package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import io.lettuce.core.ScriptOutputType;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoLocation;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

@RunWith(SpringRunner.class)
@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private IShopService service;
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void testSaveToRedis() throws InterruptedException {
        Shop shop = service.getById(1);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1,shop,10L, TimeUnit.SECONDS);
    }

    @Test
    void testIdWorker(){
        for (int i = 0; i < 10000; i++) {
            long orderId = redisIdWorker.nextId("order");
            System.out.println("id = " + orderId);
        }
    }
    @Test
    void importShopData(){
        //查询所有商铺
        List<Shop> list = service.list();
        //按类型分类
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //以类型为key 将x ，y坐标和商铺id存入redis总
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            String key = SHOP_GEO_KEY + entry.getKey();
            List<Shop> value = entry.getValue();
            Map<String , Point> pointMap = new HashMap<>(value.size());
            for (Shop shop : value) {
                Long shopId = shop.getId();
                Double x = shop.getX();
                Double y = shop.getY();
                pointMap.put(shopId.toString(),new Point(x,y));
            }
            stringRedisTemplate.opsForGeo().add(key,pointMap);
        }
    }
    @Test
    void testHyperLogLog(){
        String key = "hl2";
        String[] values = new String[1000];
        for (int i = 0; i < 1000000; i++) {
            values[i % 1000] = "user_" + i;
            if (i % 1000 == 999){
                stringRedisTemplate.opsForHyperLogLog().add(key,values);
            }
        }
        Long count = stringRedisTemplate.opsForHyperLogLog().size(key);
        System.out.println(count);
    }
}
