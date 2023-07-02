package you.chanwill.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import you.chanwill.mapper.SeckillVoucher;
import you.chanwill.mapper.VoucherOrderMapper;
import you.chanwill.pojo.VoucherOrder;
import you.chanwill.utils.RedisIdWorker;
import you.chanwill.utils.Result;
import you.chanwill.utils.UserHolder;

import java.time.LocalDateTime;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    // 优惠卷秒杀功能实现
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 查询余量
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 判断活动是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("活动尚未开始");
        }
        // 判断活动是否结束
        if (voucher.getBeginTime().isBefore(LocalDateTime.now())) {
            return Result.fail("活动已经结束");
        }
        // 判断库存是否充足
        if (voucher.getStock() < 1){
            return Result.fail("库存不足");
        }

        Long UserId = UserHolder.getUser().getId();
        synchronized(UserId.toString().intern()) {
            // 获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatoVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result creatoVoucherOrder(Long voucherId){
        // 实现一人一单
        Long UserId = UserHolder.getUser().getId();

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
    }
}
