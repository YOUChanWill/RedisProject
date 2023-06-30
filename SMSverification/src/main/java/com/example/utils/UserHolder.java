package com.example.utils;

import com.example.dto.UserDTO;

public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static void removeUser(){
        tl.remove();
    }

    public static UserDTO getUser(){return tl.get();}
}
