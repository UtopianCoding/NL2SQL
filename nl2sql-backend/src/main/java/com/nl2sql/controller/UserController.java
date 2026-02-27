package com.nl2sql.controller;

import com.nl2sql.common.Result;
import com.nl2sql.model.dto.ChangePasswordRequest;
import com.nl2sql.model.dto.LoginResponse;
import com.nl2sql.model.dto.UpdateUserRequest;
import com.nl2sql.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@Tag(name = "用户管理", description = "用户信息更新、密码修改")
public class UserController {

    @Autowired
    private UserService userService;

    @PutMapping("/profile")
    @Operation(summary = "更新用户信息")
    public Result<LoginResponse.UserInfo> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateUserRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        LoginResponse.UserInfo userInfo = userService.updateUser(userId, request);
        return Result.success(userInfo);
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码")
    public Result<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        userService.changePassword(userId, request);
        return Result.success();
    }
}
