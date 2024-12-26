package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@Component
public class RefreshTokenIntercepter implements HandlerInterceptor {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //从request中获取token
        String token = request.getHeader("authorization");
        if (token == null) {
            return true;
        }
        //根据token从redis中获取用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        //如果用户为null 拦截 返回401状态码
        if (userMap.isEmpty()) {
            return true;
        }
        //将用户转为对象
        UserDTO userDTO = BeanUtil.mapToBean(userMap, UserDTO.class, false);
        //若有 则将用户存到threadlocal中 方便controller层获取
        UserHolder.saveUser(userDTO);
        //刷新token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //一次http请求结束后 线程结束 需要将threadlocal中的数据移除 防止内存泄漏
        //controller层返回给前端后 需要将user移除 防止内存泄露
        UserHolder.removeUser();
    }
}
