package mao.spring_boot_redis_hmdp.service;


import com.baomidou.mybatisplus.extension.service.IService;
import mao.spring_boot_redis_hmdp.dto.Result;
import mao.spring_boot_redis_hmdp.entity.Blog;


public interface IBlogService extends IService<Blog>
{
    /**
     * 查询热门的探店笔记
     *
     * @param current 当前页
     * @return Result
     */
    Result queryHotBlog(Integer current);

    /**
     * 根据id进行查询
     *
     * @param id id
     * @return Result
     */
    Result queryBlogById(String id);

    /**
     * 点赞功能
     *
     * @param id id
     * @return result
     */
    Result likeBlog(Long id);

    /**
     * 点赞排行榜
     *
     * @param id id
     * @return Result
     */
    Result queryBlogLikes(String id);

    /**
     * 保存（发布）博客信息
     *
     * @param blog Blog
     * @return Result
     */
    Result saveBlog(Blog blog);
}
