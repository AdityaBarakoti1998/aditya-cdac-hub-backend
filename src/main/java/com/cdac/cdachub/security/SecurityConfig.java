package com.cdac.cdachub.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
	
	@Value("${app.frontend.url}")
	private String frontendUrl;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    // Allow React frontend to talk to Spring Boot
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendUrl));
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpStatus.UNAUTHORIZED.value());
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Unauthorized\"}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(HttpStatus.FORBIDDEN.value());
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Forbidden\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
            	    .requestMatchers("/error").permitAll()
            	    .requestMatchers("/actuator/health").permitAll()
            	    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            	    .requestMatchers("/uploads/**").permitAll()
            	    .requestMatchers("/api/public/**").permitAll()
            	    .requestMatchers("/api/auth/refresh").permitAll()
            	    .requestMatchers("/oauth2/**", "/login/**").permitAll()
            	    
            	    
            	    
            	 // ✅ STUDENTS, REVIEWERS, and ADMINS can submit projects
            	    .requestMatchers("/api/student/**").hasAnyAuthority("STUDENT", "ROLE_STUDENT", "REVIEWER", "ROLE_REVIEWER", "ADMIN", "ROLE_ADMIN")
            	    
            	    // ✅ MISSING LINE RESTORED: Only REVIEWERS and ADMINS can approve/reject projects!
            	    .requestMatchers("/api/reviewer/**").hasAnyAuthority("REVIEWER", "ROLE_REVIEWER", "ADMIN", "ROLE_ADMIN")
            	    //  MUST use hasAnyAuthority to prevent ROLE_ mismatch 403 errors
            
            	 //  Added REVIEWER to the list so they can submit projects too!
            	    .requestMatchers("/api/student/**").hasAnyAuthority("STUDENT", "ROLE_STUDENT", "REVIEWER", "ROLE_REVIEWER", "ADMIN", "ROLE_ADMIN")
            	    .requestMatchers("/api/admin/**").hasAnyAuthority("ADMIN", "ROLE_ADMIN")
            	    .requestMatchers("/api/user/admin/**").hasAnyAuthority("ADMIN", "ROLE_ADMIN")
            	    
            	    .requestMatchers("/api/user/**").authenticated()
            	    .anyRequest().authenticated()
            	)
            .oauth2Login(oauth -> oauth
                .successHandler(oAuth2SuccessHandler)
            )
            
            // Clean, correct filter chain order
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitFilter, JwtAuthFilter.class);
          
        return http.build();
    }
}