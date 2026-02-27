package com.nl2sql.service.user;

import com.nl2sql.model.dto.ChangePasswordRequest;
import com.nl2sql.model.dto.LoginResponse;
import com.nl2sql.model.dto.UpdateUserRequest;

public interface UserService {

    /**
     * 更新用户信息
     */
    LoginResponse.UserInfo updateUser(Long userId, UpdateUserRequest request);

    /**
     * 修改密码
     */
    void changePassword(Long userId, ChangePasswordRequest request);
}
