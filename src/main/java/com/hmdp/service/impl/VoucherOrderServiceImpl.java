package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private IVoucherOrderService voucherOrderService;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //判断是否在有效时间内
        LocalDateTime now = LocalDateTime.now();
        if (seckillVoucher.getBeginTime().isAfter(now)){
            return Result.fail("秒杀尚未开始");
        }
        if (seckillVoucher.getEndTime().isBefore(now)){
            return Result.fail("秒杀已经结束");
        }
        //判断库存是否充足
        if (seckillVoucher.getStock() < 1 ) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //锁对象为userId在字符串常量池的引用 只会锁住id相同的用户
        synchronized (userId.toString().intern()){
            //由于自身调用会不经过代理而导致事务失效 所以可采取两种方法
            //1.注入自身的接口（JDK 动态代理只能代理接口，而不能直接代理类）
            // 再通过接口的代理对象调用此方法 使得事务生效 voucherOrderService.createVoucherOrder(voucherId)
            //2.通过aopcontext调用currentProxy方法获取当前对象的代理对象（类型为当前类的接口）
            //再通过代理对象调用此方法 使得事务生效（1.引入aspectjweaver的依赖 2.启动类添加@enableaspectj...注解暴露代理对象）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //检查是否已存在订单
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已购买过");
        }
        //若不存在且库存充足 库存减一
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                //为防止多线程并发问题 应设置乐观锁 即stock应该与之前查到的一致 但是这样会
                //造成不必要的失败 所以当库存大于0时都可以执行减一
                .gt("stock", 0).update();
        if (!success) {
            return Result.fail("库存不足");
        }
        //创建订单
        //生成id
        long id = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(id);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //返回订单编号
        return Result.ok(id);
    }
}

