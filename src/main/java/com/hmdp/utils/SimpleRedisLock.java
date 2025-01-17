package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class SimpleRedisLock implements ILock{
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String KEY_PREFIX = "lock:";
    //UUID获取每个jvm中的唯一表示（static跟随类加载）
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    public SimpleRedisLock(String name) {
        this.name = name;
    }
    @Override
    public boolean tryLock(long timeoutSec) {
        String key = KEY_PREFIX + name;
        //获取每个线程的唯一标识
        long id = Thread.currentThread().getId();
        String  value = ID_PREFIX + id;
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    //利用lua脚本解决释放锁的原子性问题（查询和删除都在lua脚本中）
    @Override
    public void unlock() {
        //调用lua脚本
       stringRedisTemplate.execute(
               UNLOCK_SCRIPT,
               Collections.singletonList(KEY_PREFIX + name),
               ID_PREFIX + Thread.currentThread().getId()
       );
    }

//    @Override
//    public void unlock() {
//        String key = KEY_PREFIX + name;
//        //根据key获取redis中的value
//        String redisValue = stringRedisTemplate.opsForValue().get(key);
//        //获取当前jvm当前线程生成的value
//        String value = ID_PREFIX + Thread.currentThread().getId();
//        //比较两者是否一致 若一致 则释放锁 不一致则不进行操作
//        if (redisValue.equals(value)) {
//            stringRedisTemplate.delete(key);
//        }
//    }
}
