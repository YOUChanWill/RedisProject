package com.example.config;

import com.example.utils.LoginInterceptor;
import com.example.utils.RefreshTokeninterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login"
                ).order(1);// 调整优先级，越小先执行
        registry.addInterceptor(new RefreshTokeninterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
