package com.pm.authservice.service;


import com.pm.authservice.dto.LoginRequestDTO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


import java.util.Optional;

@Service
public class AuthService {
    private final UserService userService;

    private final PasswordEncoder passwordEncoder;
    public AuthService(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<String> authenticate(LoginRequestDTO loginRequestDTO){
        Optional<String> token = userService.findByEmail(loginRequestDTO.getEmail())
                .filter(user -> passwordEncoder.matches(loginRequestDTO.getPassword(),
                        user.getPassword()))
                .map(user -> jwtUtil.generateToken(user.getEmail(), user.getRole()));
        return token;
    }

}
