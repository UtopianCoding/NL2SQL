package com.nl2sql.service.auth;

import com.nl2sql.model.dto.LoginRequest;
import com.nl2sql.model.dto.LoginResponse;
import com.nl2sql.model.entity.User;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    User getCurrentUser(Long userId);
}
