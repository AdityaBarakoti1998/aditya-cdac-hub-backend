package com.cdac.cdachub.security;

import com.cdac.cdachub.model.User;
import com.cdac.cdachub.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

//What this does-->
////Because it extends OncePerRequestFilter, it automatically intercepts every single HTTP request 
//exactly one time before the request is allowed to reach your controllers. Its only job is to check if the 
//person making the request has a valid ID (the JWT token) and to tell Spring Security who they are.


// @Component tells Spring to create an instance of this class and manage it.
// @RequiredArgsConstructor automatically creates a constructor for our 'final' variables.
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil; // Helper class used to decode and verify the token
    private final UserRepository userRepository; // Used to fetch user details from the database

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // STEP 1 & 2: Check the "ID Card" format
        // The frontend sends the token in the header like this: "Authorization: Bearer <token_string>"
        String authHeader = request.getHeader("Authorization");

        // If there is no header, or it doesn't start with "Bearer ", this request is either 
        // public (like logging in) or unauthorized. We pass it along the chain without setting security.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // STEP 3: Strip the word "Bearer " (which is 7 characters long) to get the raw token string
        String token = authHeader.substring(7);

        // STEP 4: Check if the token is fake or expired
        // This uses the secret key in your application.properties to ensure no one tampered with it.
        if (!jwtUtil.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return; // Stop processing security if token is invalid
        }

        // STEP 5: Extract the user's identity (email) from the token's payload
        String email = jwtUtil.extractEmail(token);

        // STEP 6: Verify the user actually still exists in your database
        // Even if the token is valid, the user might have been deleted from the database yesterday.
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // STEP 7: Give the user their VIP pass (Authentication Token)
        // Now that we know who they are, we create an official Spring Security authentication object.
        // We attach their Email and their Role (e.g., ROLE_STUDENT, ROLE_ADMIN).
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                email,  // Who they are
                null,   // Passwords are not needed here because the JWT proves they already logged in
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())) // Their permissions
            );

        // We place this authentication object into the SecurityContextHolder.
        // This is like stamping their hand. Now, any controller can ask "Who is logged in?" and get the answer.
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // STEP 8: Open the door
        // Now that the security context is set, we let the request continue to its destination (the controller).
        filterChain.doFilter(request, response);
    }
}