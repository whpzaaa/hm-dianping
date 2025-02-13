package com.hmdp.service.impl;

import cn.hutool.Hutool;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.sql.Struct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IFollowService followService;
    @Autowired
    private IBlogService blogService;
    @Override
    public Result queryBlogById(Long id) {
        //根据id查询博客
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("blog不存在");
        }
        //根据博客的用户id查询用户
        queryBlogUser(blog);
        //查询用户是否已点赞
        isLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        //获取用户
        UserDTO user = UserHolder.getUser();
        //若用户为空即未登录 直接返回
        if (user == null) {
            return Result.ok();
        }
        //若用户存在 则查询zset集合的分数
        String key = BLOG_LIKED_KEY + id;
        Long userId = user.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //若分数存在 则用户已点赞 即取消点赞
        if (score != null) {
            //1.点赞数减一
            update().setSql("liked = liked - 1").eq("id" ,id).update();
            //2.将用户移除
            stringRedisTemplate.opsForZSet().remove(key,userId.toString());
        }else {
            //若不存在 则用户未点赞 设置点赞
            //1.点赞数加一
            update().setSql("liked = liked + 1").eq("id" ,id).update();
            //2.将用户和时间戳放入zset集合
            stringRedisTemplate.opsForZSet().add(key,userId.toString(), System.currentTimeMillis());
        }
        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            queryBlogUser(blog);
            isLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //查询redis中排名前五的用户
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //若没人点赞 则返回空集合
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //将set集合转为Long类型的list集合
        List<Long> ids = top5.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        String idsStr = StrUtil.join(",", ids);
        //再根据ids查询用户
        List<User> list = userService.query()
                .in("id", ids)
                .last("order by field (id,"+ idsStr +")")
                .list();
        List<UserDTO> userDTOList = list.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return  Result.ok();
        }
        blog.setUserId(user.getId());
        // 保存探店博文
        save(blog);
        //将博客id推送给所有粉丝
        //1.查询用户的粉丝
        Long blogId = blog.getId();
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        //2.以用户为key将博客id和时间戳存入redis中
        for (Follow follow : follows) {
            String key = FEED_KEY + follow.getUserId().toString();
            stringRedisTemplate.opsForZSet().add(key,blogId.toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blogId);
    }

    @Override
    public Result queryBlogFollow(Long max, Integer offset) {
        //获取当前用户
        UserDTO user = UserHolder.getUser();
        //若为空则直接返回
        if (user == null) {
            return  Result.ok();
        }
        //查询收件箱
        String key = FEED_KEY + user.getId();
        //0为最小值 max为最大值 也即上次查询的最小值 offset为从小于等于最大值的第几个开始取 也即上一次查询与最小值相同的个数 2为查询几个
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //获取博客ids 和最小值以及offset
        if (typedTuples == null || typedTuples.isEmpty()) {
            return  Result.ok();
        }
        //用于记录最小值
        Long minTime = 0L;
        //用于记录相同分数的个数
        Integer count = 1;
        List<Long> ids = new ArrayList<>(typedTuples.size());
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            Long id = Long.valueOf(typedTuple.getValue());
            long time = typedTuple.getScore().longValue();
            ids.add(id);
            //如果当前值与最小值相同则count++
            if (minTime == time) {
                count ++ ;
            } else {
                //若当前值与最小值不同 即当前值比最小值要小 则计数器重新为1 并修改最小值
                count = 1 ;
                minTime = time;
            }
        }
        //利用ids查询博客 返回scrollresult
        String idsStr = StrUtil.join(",", ids);
        List<Blog> list = blogService.query().in("id", ids).last("order by field (id," + idsStr + ")").list();
        for (Blog blog : list) {
            //根据博客的用户id查询用户
            queryBlogUser(blog);
            //查询用户是否已点赞
            isLiked(blog);
        }
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(list);
        scrollResult.setOffset(count);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    private void isLiked(Blog blog){
        UserDTO user = UserHolder.getUser();
        //若用户为空即未登录 直接返回
        if (user == null) {
            return;
        }
        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
