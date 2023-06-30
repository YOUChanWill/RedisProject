package com.example.controller;

import com.example.pojo.Login;
import com.example.dto.Result;
import com.example.service.IUserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sedCode(phone,session);
    }

    @PostMapping("/login")
    public Result login(@RequestBody Login loginForm, HttpSession session){
        // 实现登录功能
        return userService.login(loginForm, session);
    }

}

