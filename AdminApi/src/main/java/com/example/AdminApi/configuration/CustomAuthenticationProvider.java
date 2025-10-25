package com.example.AdminApi.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Custom Authentication Provider with detailed logging.
 * Handles username/password authentication with BCrypt password verification.
 *
 * Note: This is created as a bean in SecurityConfig to avoid circular dependencies.
 */
@Slf4j
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public CustomAuthenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        log.info("🔐 [AUTH STEP 2] CustomAuthenticationProvider.authenticate() called");
        log.debug("Authentication attempt for username: {}", username);

        // Load user from database
        UserDetails user = userDetailsService.loadUserByUsername(username);

        // Verify password with BCrypt
        log.info("🔐 [AUTH STEP 5] Verifying password with BCryptPasswordEncoder...");
        log.debug("Comparing provided password with stored BCrypt hash");

        boolean passwordMatches = passwordEncoder.matches(password, user.getPassword());

        if (!passwordMatches) {
            log.error("❌ [AUTH FAILED] Password verification failed for user: {}", username);
            log.debug("Provided password does not match stored hash");
            throw new BadCredentialsException("Invalid username or password");
        }

        log.info("✅ [AUTH STEP 6] Password verified successfully!");
        log.info("✅ [AUTH SUCCESS] User authenticated: username={}, authorities={}",
            username, user.getAuthorities());

        // Create authenticated token
        UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(user, password, user.getAuthorities());

        log.debug("Authentication token created with authorities: {}", user.getAuthorities());

        return authToken;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
