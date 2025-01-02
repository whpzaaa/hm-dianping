package com.hmdp.controller;


import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopTypeService typeService;
    //@Cacheable(cacheNames = "cache", key = "'shop-type'")
    @GetMapping("list")
    public Result queryTypeList() {
        String key = "cache:shop-type";
        List<String> list = stringRedisTemplate.opsForList().range(key, 0, -1);
        List<ShopType> typeList = new ArrayList<>();
        //如果存在 直接返回集合
        if (list != null && list.size() > 0) {
            for (String s : list) {
                ShopType shopType = JSONUtil.toBean(s, ShopType.class);
                typeList.add(shopType);
            }
            return Result.ok(typeList);
        }
        //不存在 查询数据库
        typeList = typeService
                .query().orderByAsc("sort").list();
        for (ShopType shopType : typeList) {
            //通过list将数据存入redis
            stringRedisTemplate.opsForList().rightPush(key,JSONUtil.toJsonStr(shopType));
            //设置ttl
            stringRedisTemplate.expire(key,30, TimeUnit.MINUTES);
        }
        return Result.ok(typeList);
    }
}
