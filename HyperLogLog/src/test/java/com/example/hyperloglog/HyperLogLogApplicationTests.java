package com.example.hyperloglog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class HyperLogLogApplicationTests {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void test() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999){
                // 发送到redis
                stringRedisTemplate.opsForHyperLogLog().add("hl1",values);
            }
        }
        // 统计数量
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl1");
        System.out.println(count);
    }
}
