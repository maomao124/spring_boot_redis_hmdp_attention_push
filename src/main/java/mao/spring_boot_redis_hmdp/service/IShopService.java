package mao.spring_boot_redis_hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import mao.spring_boot_redis_hmdp.dto.Result;
import mao.spring_boot_redis_hmdp.entity.Shop;


public interface IShopService extends IService<Shop>
{
    /**
     * 根据id查询商户信息，有缓存
     *
     * @param id 商户的id
     * @return Result
     */
    Result queryShopById(Long id);

    /**
     * 更新商户信息，包含更新缓存
     *
     * @param shop 商户信息
     * @return Result
     */
    Result updateShop(Shop shop);
}
