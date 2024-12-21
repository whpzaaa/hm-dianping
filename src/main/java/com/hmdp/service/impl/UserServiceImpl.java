package com.hmdp.service.impl;

import cn.hutool.Hutool;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Random;

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
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //先检验手机号是否合法
        //若不合法 返回错误
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //若合法 生成验证码
        String code = RandomUtil.randomNumbers(6);
        //讲验证码存到session中
        session.setAttribute("code", code);
        //发送验证码
        log.debug("发送的验证码是：" + code);
        return Result.ok();
    }
}
