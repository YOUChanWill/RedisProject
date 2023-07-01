package you.chanwill.service;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import you.chanwill.utils.CacheTools;
import you.chanwill.utils.RedisData;
import you.chanwill.dto.Result;
import you.chanwill.mapper.ShopMapper;
import you.chanwill.pojo.Shop;
import you.chanwill.service.impl.IShopService;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 使用工具类实现缓存穿透
    @Resource
    private CacheTools cacheTools;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//         Shop shop = queryWithPassThrough(id);
        // 缓存击穿(使用互斥锁)
//         Shop shop = queryWithMutex(id);
        // 使用工具类实现缓存穿透
//        Shop shop = cacheTools.queryWithPassThrough("cache:shop", id, Shop.class, this::getById, 30L, TimeUnit.MINUTES);

        // 使用工具类实现逻辑过期解决缓存穿透
        Shop shop = cacheTools.queryWithLogicalExpire("cache:shop", id, Shop.class, this::getById, 30L, TimeUnit.MINUTES);

        if (shop == null){
            return Result.fail("信息不存在");
        }
        return Result.ok(shop);
    }

    // 增加锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    // 删除锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    // 解决缓存击穿
    public Shop queryWithMutex(Long id){
        String shopjson = stringRedisTemplate.opsForValue().get("cache:shop" + id);
        // 判断是否存在
        if (StrUtil.isNotBlank(shopjson)) {
            return JSONUtil.toBean(shopjson, Shop.class);
        }
        // 判断命中的是否是空值
        if (shopjson != null){
            return null;
        }

        // 实现缓存重建（获取互斥锁、判断是否成功、失败则休眠并重试）
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            if (!isLock){
                //若失败则休眠并且重试
                Thread.sleep(50);
                return queryWithMutex(id);// 使用递归
            }

            // 如果不在redis中，那么从数据库进行查询
            shop = getById(id);
            if (shop == null){
                // 将空值写入redis，解决缓存穿透（缓存null值）
                stringRedisTemplate.opsForValue().set("cache:shop" + id,"",2,TimeUnit.MINUTES);
                return null;
            }
            // 查询到后写入redis，添加过期时间
            stringRedisTemplate.opsForValue().set("cache:shop" + id,JSONUtil.toJsonStr(shop),30, TimeUnit.MINUTES);
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            // 释放互斥锁
            unLock(lockKey);
        }
        return shop;
    }

    // 逻辑过期
    private void saveShopRedis(Long id,Long expireSecond){
        Shop shop = getById(id);
        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond));
        // 写入Redis
        stringRedisTemplate.opsForValue().set("cache:shop" + id,JSONUtil.toJsonStr(redisData));
    }

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 解决缓存击穿，逻辑过期
    public Shop queryWithLogicalExpire(Long id){
        String shopjson = stringRedisTemplate.opsForValue().get("cache:shop" + id);
        // 判断是否存在,不存在直接返回null
        if (StrUtil.isBlank(shopjson)) {
            return null;
        }
        // 若命中先把json反序列化
        RedisData redisData = JSONUtil.toBean(shopjson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);// 获取店铺信息
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            // 未过期，返回店铺信息
            return shop;
        }
        // 过期则需要重建缓存
        String lockKey = "lock:shop:" + id;
        boolean isLock = tryLock(lockKey);
        if (isLock){
            // 是否获取锁成功，若成功开启独立线程
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    // 重建缓存
                    this.saveShopRedis(id,1800L); // 时间设为30分钟
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }
        return shop;
    }

    // 解决缓存穿透
    public Shop queryWithPassThrough(Long id){
        String shopjson = stringRedisTemplate.opsForValue().get("cache:shop" + id);
        // 判断是否存在
        if (StrUtil.isNotBlank(shopjson)) {
            return JSONUtil.toBean(shopjson, Shop.class);
        }
        // 判断命中的是否是空值
        if (shopjson != null){
            return null;
        }

        // 如果不在redis中，那么从MySQL进行查询
        Shop shop = getById(id);
        if (shop == null){
            // 将空值写入redis，解决缓存穿透（缓存null值）
            stringRedisTemplate.opsForValue().set("cache:shop" + id,"",2,TimeUnit.MINUTES);
            return null;
        }
        // 查询到后写入redis，添加过期时间
        stringRedisTemplate.opsForValue().set("cache:shop" + id,JSONUtil.toJsonStr(shop),30, TimeUnit.MINUTES);

        return shop;
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("id不能为空");
        }
        // 更新数据库
        updateById(shop);
        // 删除redis中的缓存
        stringRedisTemplate.delete("cache:shop" + id);
        return Result.ok();
    }
}
