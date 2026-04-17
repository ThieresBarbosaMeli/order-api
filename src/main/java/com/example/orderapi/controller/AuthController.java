package com.example.orderapi.controller;

import com.example.orderapi.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final String adminUser;
    private final String adminPassword;

    public AuthController(
            JwtUtil jwtUtil,
            @Value("${auth.admin.username}") String adminUser,
            @Value("${auth.admin.password}") String adminPassword
    ) {
        this.jwtUtil = jwtUtil;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (adminUser.equals(username) && adminPassword.equals(password)) {
            return ResponseEntity.ok(Map.of("token", jwtUtil.generate(username)));
        }

        return ResponseEntity.status(401).body(Map.of("error", "Credenciais inválidas."));
    }
}
