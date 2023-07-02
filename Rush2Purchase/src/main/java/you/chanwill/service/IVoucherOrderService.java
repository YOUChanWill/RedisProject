package you.chanwill.service;

import com.baomidou.mybatisplus.extension.service.IService;
import you.chanwill.pojo.VoucherOrder;
import you.chanwill.utils.Result;

public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result seckillVoucher(Long voucherId);

    Result creatoVoucherOrder(Long voucherId);
}
