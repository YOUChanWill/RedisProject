package you.chanwill.controller;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import you.chanwill.dto.Result;
import you.chanwill.pojo.Shop;
import you.chanwill.service.impl.IShopService;


@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
            return Result.ok(shopService.queryById(id));
        }

    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // 写入数据库
        return shopService.update(shop);
    }

}

