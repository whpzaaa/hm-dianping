package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;
    @Override
    public Result follow(Long followUserId, Boolean isfollow) {
        //获取当前用户
        UserDTO user = UserHolder.getUser();
        //若未登录 则直接返回
        if (user == null) {
            return Result.ok();
        }
        Long userId = user.getId();
        String key = "follows:" + userId;
        //如果是关注 则插入数据
        if (isfollow) {
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            boolean isSuccess = save(follow);
            //如果成功插入 则向redis中放入该用户
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else {
            //若为取关 则删除数据
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        UserDTO user = UserHolder.getUser();
        //若未登录 则直接返回
        if (user == null) {
            return Result.ok();
        }
        Long userId = user.getId();
        //查询数据库
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        //如果coun>0即存在数据 返回true
        return Result.ok(count > 0);
    }

    @Override
    public Result commonFollows(Long id) {
        String key1 = "follows:" + id.toString();
        //获取当前用户
        UserDTO user = UserHolder.getUser();
        //如果未登录 则返回空集合
        if (user == null) {
            return Result.ok(Collections.emptyList());
        }
        String key2 = "follows:" + user.getId();
        //∩运算 取两人关注列表的交集 即共同关注
        Set<String> set = stringRedisTemplate.opsForSet().intersect(key1, key2);
        //如果交集为空 则返回空集合
        if (set == null || set.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //不为空 则将set集合转为long型
        List<Long> ids = set.stream().map(Long::valueOf).collect(Collectors.toList());
        //再将user类型转为userdto类型
        List<UserDTO> list = userService.query().in("id",ids).list()
                .stream()
                .map(user1 -> BeanUtil.copyProperties(user1, UserDTO.class))
                .collect(Collectors.toList());
        //返回共同关注用户集合
        return Result.ok(list);
    }
}
