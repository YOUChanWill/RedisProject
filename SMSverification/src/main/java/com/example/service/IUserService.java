package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.pojo.Login;
import com.example.dto.Result;
import com.example.pojo.User;
import jakarta.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    Result login(Login loginForm, HttpSession session);

    Result sedCode(String phone, HttpSession session);
}
