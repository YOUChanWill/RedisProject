package com.example.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class Contraller {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("/get/{key}")
    public String hi(@PathVariable String key){
        return stringRedisTemplate.opsForValue().get(key);
    }

    @GetMapping("/set/{key}/{value}")
    public String hi(@PathVariable String key,@PathVariable String value){
        stringRedisTemplate.opsForValue().set(key,value);
        return "success";
    }



}
