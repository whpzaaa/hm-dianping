package com.hmdp.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
         //使用 Jackson2JsonRedisSerializer 来进行 JSON 序列化
        //Jackson2JsonRedisSerializer<Object> jsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10));// 默认TTL为10分钟
         //       .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonRedisSerializer));
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

