package com.example.AdminApi.configuration;

import com.example.AdminApi.services.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for authentication and authorization.
 *
 * Authentication: HTTP Basic Auth with BCrypt password encoding
 * Authorization: Role-Based Access Control (RBAC)
 *   - ADMIN: Full access to all endpoints
 *   - VIEWER: Read-only access to rules and stats
 *
 * Public endpoints (no auth required):
 *   - POST /api/transactions (public transaction submission)
 *   - /actuator/** (monitoring endpoints)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for API (can enable later with frontend integration)
            .csrf(csrf -> csrf.disable())

            // Session management: stateless (no sessions, authenticate each request)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - no authentication required
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/transactions").permitAll()

                // Static files - allow access to login page and assets
                .requestMatchers(
                    "/",
                    "/login.html",
                    "/dashboard.html",
                    "/css/**",
                    "/js/**",
                    "/favicon.ico"
                ).permitAll()

                // Admin endpoints - ADMIN only
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Auth endpoints - authenticated users only
                .requestMatchers("/api/auth/**").authenticated()

                // Rules API - different permissions by HTTP method
                .requestMatchers(HttpMethod.GET, "/api/rules/**").hasAnyRole("ADMIN", "VIEWER")
                .requestMatchers(HttpMethod.POST, "/api/rules").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/rules/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/rules/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/rules/**").hasRole("ADMIN")

                // Transactions API - different permissions by HTTP method
                .requestMatchers(HttpMethod.GET, "/api/transactions/**").hasAnyRole("ADMIN", "VIEWER")
                .requestMatchers(HttpMethod.POST, "/api/transactions").permitAll() // Already covered above but explicit

                // Stats endpoints - ADMIN or VIEWER
                .requestMatchers("/api/stats/**").hasAnyRole("ADMIN", "VIEWER")

                // All other endpoints require authentication
                .anyRequest().authenticated()
            )

            // HTTP Basic Authentication without browser popup
            // Custom entry point that returns 401 without WWW-Authenticate header
            .httpBasic(basic -> basic
                .authenticationEntryPoint((request, response, authException) -> {
                    log.warn("🔐 [AUTH STEP 1] HTTP Basic Auth initiated - Request to: {} from IP: {}",
                        request.getRequestURI(), request.getRemoteAddr());
                    log.info("❌ [AUTH FAILED] Unauthorized access attempt: {}", authException.getMessage());

                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                })
            )

            // Use our custom authentication provider
            .authenticationProvider(authenticationProvider());

        log.info("✅ SecurityFilterChain configured with CustomAuthenticationProvider");
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        log.debug("Creating BCryptPasswordEncoder bean");
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CustomAuthenticationProvider authenticationProvider() {
        log.info("Creating CustomAuthenticationProvider bean with UserDetailsService and PasswordEncoder");
        return new CustomAuthenticationProvider(userDetailsService, passwordEncoder());
    }
}
