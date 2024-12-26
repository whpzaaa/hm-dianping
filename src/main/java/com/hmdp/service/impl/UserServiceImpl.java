package com.hmdp.service.impl;

import cn.hutool.Hutool;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.dto.UserLoginDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //先检验手机号是否合法
        //若不合法 返回错误
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //若合法 生成验证码
        String code = RandomUtil.randomNumbers(6);
        //讲验证码存到redis中 并和手机号绑定
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code , LOGIN_CODE_TTL , TimeUnit.MINUTES);
        //发送验证码
        log.debug("发送的验证码是：" + code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //先校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误");
        }
        //再根据手机号获取redis中的验证码
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        //与传来的验证码比较
        String userCode = loginForm.getCode();
        //若不一致 则返回错误
        if (code == null || !userCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        //若一致 则根据电话查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();
        //若用户不存在 则注册该用户
        if (user == null) {
            user = new User();
            user.setPhone(loginForm.getPhone());
            user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
            save(user);
        }
        //将用户存入redis中
        //隐藏用户信息 需存入userdto
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //利用uuid生成token 作为登录令牌和redis中的key
        String token = UUID.randomUUID().toString(true);
        String id = userDTO.getId().toString();
        UserLoginDTO userLoginDTO = new UserLoginDTO();
        userLoginDTO.setIcon(userDTO.getIcon());
        userLoginDTO.setId(id);
        userLoginDTO.setNickName(userDTO.getNickName());
        //将userdto 转为hash结构
        Map<String, Object> userMap = BeanUtil.beanToMap(userLoginDTO);
        //写入redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token,userMap);
        //设置token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL,TimeUnit.MINUTES);
        //返回token
        return Result.ok(token);
    }
}
