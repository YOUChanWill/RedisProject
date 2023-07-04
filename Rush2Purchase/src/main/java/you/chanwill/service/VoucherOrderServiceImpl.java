package you.chanwill.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import you.chanwill.mapper.VoucherOrderMapper;
import you.chanwill.pojo.VoucherOrder;
import you.chanwill.utils.RedisIdWorker;
import you.chanwill.utils.Result;
import you.chanwill.utils.UserHolder;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{
        String queueName = "streams.order";
        @Override
        public void run() {
            while (true){
                try {
                    // 获取消息队列中的信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 判断消息是否成功
                    if (list == null || list.isEmpty()){
                        continue;
                    }
                    // 获取订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 完成下单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                }catch (Exception e){
                    log.error("处理异常",e);
                    handlePendingList();
                }
            }
        }
        private void handlePendingList() {
            while (true){
                try {
                    // 获取PendingList中的信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 判断消息是否成功
                    if (list == null || list.isEmpty()){
                        break;
                    }
                    // 获取订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 完成下单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                }catch (Exception e){
                    log.error("处理pendingList异常",e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
    }

    // 加载脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private IVoucherOrderService proxy;

    // 优惠卷秒杀功能实现
    // 使用消息队列
    @Override
    public Result seckillVoucher(Long voucherId) {

        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 获取订单
        Long orderId = redisIdWorker.nextId("order");
        // 执行lua脚本,如果有购买资格则把信息保存在消息队列
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        // 判断结果是否为0
        int r = result.intValue();
        if (r != 0){
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);

    }

    @Transactional
    public Result creatoVoucherOrder(Long voucherId){
        // 实现一人一单
        Long UserId = UserHolder.getUser().getId();

        // 使用redisson获取锁
        RLock lock = redissonClient.getLock("lock:order" + UserId);
        // 获取锁
        boolean isLock = lock.tryLock();
        if (!isLock){
            // 获取锁失败
            return Result.fail("禁止重复下单");
        }

        try {
            Integer count = query().eq("user_id", UserId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("已经购买过了");
            }

            // 扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId).gt("stock", 0) // 库存大于0就可以继续操作
                    .update();
            if (!success) {
                return Result.fail("库存不足");
            }

            // 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();// 订单id,用户id,优惠券id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);

            Long userId = UserHolder.getUser().getId();
            voucherOrder.setUserId(userId);

            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            return Result.ok(orderId);

        } finally {
            // 释放锁
            lock.unlock();
        }
    }
}

