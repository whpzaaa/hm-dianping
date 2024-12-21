package com.hmdp.service.impl;

import cn.hutool.Hutool;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Random;

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

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //先校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误");
        }
        //再获取session中的验证码
        Object code = session.getAttribute("code");
        //与传来的验证码比较
        String userCode = loginForm.getCode();
        //若不一致 则返回错误
        if (code == null || !userCode.equals(code.toString())) {
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
        //将用户存入session中
        session.setAttribute("user",user);
        return Result.ok();
    }
}
