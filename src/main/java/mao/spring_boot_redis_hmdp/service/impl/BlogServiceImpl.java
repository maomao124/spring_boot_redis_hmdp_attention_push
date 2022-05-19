package mao.spring_boot_redis_hmdp.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import mao.spring_boot_redis_hmdp.dto.Result;
import mao.spring_boot_redis_hmdp.dto.UserDTO;
import mao.spring_boot_redis_hmdp.entity.Blog;
import mao.spring_boot_redis_hmdp.entity.Follow;
import mao.spring_boot_redis_hmdp.entity.User;
import mao.spring_boot_redis_hmdp.mapper.BlogMapper;
import mao.spring_boot_redis_hmdp.service.IBlogService;
import mao.spring_boot_redis_hmdp.service.IFollowService;
import mao.spring_boot_redis_hmdp.service.IUserService;
import mao.spring_boot_redis_hmdp.utils.RedisConstants;
import mao.spring_boot_redis_hmdp.utils.RedisUtils;
import mao.spring_boot_redis_hmdp.utils.SystemConstants;
import mao.spring_boot_redis_hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service("blogService")
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService
{

    @Resource
    private IUserService userService;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current)
    {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->
        {
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(String id)
    {
        //查询
        //Blog blog = this.getById(id);
        Blog blog = redisUtils.query(RedisConstants.BLOG_KEY,
                RedisConstants.LOCK_BLOG_KEY, id,
                Blog.class, this::getById,
                RedisConstants.CACHE_BLOG_TTL,
                TimeUnit.MINUTES, 120);
        //判断是否存在
        if (blog == null)
        {
            //不存在，返回
            return Result.fail("该笔记信息不存在");
        }
        //存在
        //填充用户信息
        //获得用户id
        Long userId = blog.getUserId();
        //查询
        User user = userService.getById(userId);
        //填充
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        //返回
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id)
    {
        //获取用户信息
        UserDTO user = UserHolder.getUser();
        //判断用户是否已经点赞(检查设置在key是否包含value)
        Boolean member = stringRedisTemplate.opsForSet().isMember(RedisConstants.BLOG_LIKED_KEY + id, user.getId().toString());
        if (BooleanUtil.isFalse(member))
        {
            //未点赞
            //数据库点赞数量+1
            boolean update = update().setSql("liked = liked + 1").eq("id", id).update();
            //判断是否成功
            if (update)
            {
                //成功
                //让redis数据过期
                stringRedisTemplate.delete(RedisConstants.BLOG_KEY);
                //保存用户到Redis的set集合
                stringRedisTemplate.opsForSet().add(RedisConstants.BLOG_LIKED_KEY + id, user.getId().toString());
            }

        }
        else
        {
            //已点赞，取消点赞
            //数据库点赞数量-1
            boolean update = update().setSql("liked = liked - 1").eq("id", id).update();
            //判断是否成功
            if (update)
            {
                //成功
                //让redis数据过期
                stringRedisTemplate.delete(RedisConstants.BLOG_KEY);
                //移除用户
                stringRedisTemplate.delete(RedisConstants.BLOG_LIKED_KEY + id);
            }
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(String id)
    {
        //获得key
        String redisKey = RedisConstants.BLOG_LIKED_KEY + id;
        //查询前5名的点赞的用户(从排序集中获取start和end之间的元素)
        Set<String> range = stringRedisTemplate.opsForZSet().range(redisKey, 0, 4);
        //判断
        if (range == null)
        {
            //返回空集合
            return Result.ok(Collections.emptyList());
        }
        //非空
        //解析出用户的id
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());
        //拼接
        String join = StrUtil.join(",", ids);
        //查询数据库
        List<User> users = userService.query().in("id", ids).last("order by filed(id, " + join + ")").list();
        //转换成dto
        List<UserDTO> dtoList = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).
                collect(Collectors.toList());
        //返回数据
        return Result.ok(dtoList);
    }

    @Override
    public Result saveBlog(Blog blog)
    {
        //获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        //保存探店博文
        boolean save = this.save(blog);
        //判断是否保存成功
        if (!save)
        {
            //保存失败
            return Result.ok();
        }
        //保存成功
        //先查询笔记作者的所有粉丝
        QueryWrapper<Follow> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("follow_user_id", user.getId());
        List<Follow> followList = followService.list(queryWrapper);
        //判断是否为空
        if (followList == null || followList.isEmpty())
        {
            //为空，无粉丝或者为null
            return Result.ok(blog.getId());
        }
        //不为空
        //推送给所有粉丝
        for (Follow follow : followList)
        {
            //获得用户id
            Long userId = follow.getUserId();
            //放入redis的zset集合里
            stringRedisTemplate.opsForZSet().add(RedisConstants.FEED_KEY + userId,
                    blog.getId().toString(),
                    System.currentTimeMillis());
        }
        //返回
        return Result.ok(blog.getId());
    }
}
