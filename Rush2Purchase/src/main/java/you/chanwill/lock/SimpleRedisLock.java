package you.chanwill.lock;

import org.springframework.data.redis.core.StringRedisTemplate;

public class SimpleRedisLock implements ILock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean tryLock(long timeoustSec) {
        return false;
    }

    @Override
    public void unlock() {

    }
}
