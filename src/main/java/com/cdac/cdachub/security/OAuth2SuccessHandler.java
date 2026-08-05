package com.cdac.cdachub.security;

import com.cdac.cdachub.model.RefreshToken;
import com.cdac.cdachub.model.User;
import com.cdac.cdachub.repository.RefreshTokenRepository;
import com.cdac.cdachub.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

// ✅ 1. ADDED THESE TWO IMPORTS
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository; 
    private final JwtUtil jwtUtil;

    @Value("${app.admin.emails}")
    private String adminEmailsConfig;
    
    @Value("${app.frontend.url}")
    private String frontendUrl; 

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String googleId = oAuth2User.getAttribute("sub");
        String avatar = oAuth2User.getAttribute("picture");

        // Save user if first time login
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            
            // ====================================================================
            //  2. CHANGED: Replaced the old "userCount == 0" logic with 
            // the new secure environment variable check.
            // ====================================================================
            List<String> adminEmails = Arrays.stream(adminEmailsConfig.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .toList();

            boolean isConfiguredAdmin = adminEmails.contains(email.toLowerCase());

            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setGoogleId(googleId);
            newUser.setAvatarUrl(avatar);
            newUser.setRole(isConfiguredAdmin ? User.Role.ADMIN : User.Role.STUDENT);
            // ====================================================================

            return userRepository.save(newUser);
        });

        // 1. Generate short-lived Access JWT Token
        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole().name()
        );

        // 2. Generate long-lived Refresh Token
        String refreshToken = jwtUtil.generateRefreshToken();
        
        // Ensure only one active refresh token exists per user
        refreshTokenRepository.deleteByUserEmail(user.getEmail()); 
        
        // Save the new refresh token to the database with a 30-day expiry
        refreshTokenRepository.save(RefreshToken.builder()
                .token(refreshToken)
                .userEmail(user.getEmail())
                .expiryDate(LocalDateTime.now().plusDays(30))
                .build());

        // 3. Redirect to frontend callback with BOTH tokens
        response.sendRedirect(frontendUrl + "/auth/callback?token=" + token + "&refreshToken=" + refreshToken);
    }
}