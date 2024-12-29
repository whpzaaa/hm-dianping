package com.hmdp.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)); // 默认TTL为10分钟

        // 自定义缓存区域的TTL
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        //设置shopCache类型的缓存ttl为30分钟
        cacheConfigurations.put("shopCache", defaultConfig.entryTtl(Duration.ofMinutes(CACHE_SHOP_TTL)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}

