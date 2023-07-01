package you.chanwill.service.impl;

import com.baomidou.mybatisplus.extension.service.IService;
import you.chanwill.dto.Result;
import you.chanwill.pojo.Shop;

public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);
}
