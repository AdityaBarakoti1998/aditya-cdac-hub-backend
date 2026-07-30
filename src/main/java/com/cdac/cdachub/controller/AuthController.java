package com.cdac.cdachub.controller;

import com.cdac.cdachub.model.RefreshToken;
import com.cdac.cdachub.model.User;
import com.cdac.cdachub.repository.RefreshTokenRepository;
import com.cdac.cdachub.repository.UserRepository;
import com.cdac.cdachub.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");

        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken).orElse(null);
        if (stored == null || stored.getExpiryDate().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(401).body(Map.of("error", "Refresh token invalid or expired"));
        }

        User user = userRepository.findByEmail(stored.getUserEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newAccessToken = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(Map.of("token", newAccessToken));
    }
}