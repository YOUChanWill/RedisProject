package com.example.redisdemo;

import com.example.redisdemo.pojo.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
@SpringBootTest
public class RedisHash {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testHash(){
        stringRedisTemplate.opsForHash().put("user:300","name","chan");
        stringRedisTemplate.opsForHash().put("user:300","age","23");

        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries("user:300");
        System.out.println(entries);
    }
}
