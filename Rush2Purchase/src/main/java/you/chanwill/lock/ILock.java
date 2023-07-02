package you.chanwill.lock;

public interface ILock {

    // 获取锁
    boolean tryLock(long timeoustSec);

    // 释放锁
    void unlock();
}
