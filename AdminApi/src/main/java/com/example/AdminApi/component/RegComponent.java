package com.example.AdminApi.component;

import com.example.AdminApi.enums.Role;
import com.example.AdminApi.models.User;
import com.example.AdminApi.repositories.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegComponent {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void reg2Users() {

        log.info("Start fulling");
        if (userRepository.count() == 0) {
            User user = new User(null, "admin",
                    passwordEncoder.encode("admin123"), Role.ADMIN, true);
            User user1 = new User(null, "viewer",
                    passwordEncoder.encode("viewer123"), Role.VIEWER, true);
            userRepository.save(user);
            userRepository.save(user1);
        }
        log.info("Stop fulling");

    }

}
