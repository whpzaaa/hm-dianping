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
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

@RunWith(SpringRunner.class)
@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private IShopService service;
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private RedisIdWorker redisIdWorker;
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
}
