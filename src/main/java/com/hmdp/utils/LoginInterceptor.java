package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.beans.BeanUtils;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //从request中获取session
        HttpSession session = request.getSession();
        //再从session中获取用户
        Object user = session.getAttribute("user");
        //如果用户为null 拦截 返回401状态码
        if (user == null) {
            response.setStatus(401);
            return false;
        }
        //若有 则将用户存到threadlocal中 方便controller层获取
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        UserHolder.saveUser(userDTO);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //一次http请求结束后 线程结束 需要将threadlocal中的数据移除 防止内存泄漏
        //controller层返回给前端后 需要将user移除 防止内存泄露
        UserHolder.removeUser();
    }
}
