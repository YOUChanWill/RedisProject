package you.chanwill.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class CacheTools {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    // 带逻辑时间的存储
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){

        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 解决缓存穿透
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){

        String key = keyPrefix + id;
        // 从redis查询店铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值
        if (json != null){
            return null;
        }
        // 如果不在redis中，那么从数据库进行查询
        R r = dbFallback.apply(id);

        if (r == null){
            // 将空值写入redis，解决缓存穿透（缓存null值）
            stringRedisTemplate.opsForValue().set(key,"",2,TimeUnit.MINUTES);
            return null;
        }
        // 查询到后写入redis，添加过期时间
        this.set(key,r,time,unit);

        return r;
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

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 解决缓存击穿，逻辑过期
    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){

        String key = keyPrefix + id;

        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在,不存在直接返回null
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 若命中先把json反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            // 未过期，返回店铺信息
            return r;
        }
        // 过期则需要重建缓存
        String lockKey = "lock:shop:" + id;
        boolean isLock = tryLock(lockKey);
        if (isLock){
            // 是否获取锁成功，若成功开启独立线程
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    // 重建缓存,先查询数据库，再写入缓存
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key,r1,time,unit);

                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }
        return r;
    }
}
