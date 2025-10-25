package com.example.AdminApi.repositories;

import com.example.AdminApi.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username for authentication.
     */
    Optional<User> findByUsername(String username);

    /**
     * Check if username already exists.
     */
    boolean existsByUsername(String username);
}
