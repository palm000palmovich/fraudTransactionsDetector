package com.example.AdminApi.enums;

/**
 * User roles for RBAC (Role-Based Access Control).
 *
 * ADMIN - Full access: can create, read, update, delete rules and view all data
 * VIEWER - Read-only access: can only view rules and statistics
 */
public enum Role {
    ADMIN,
    VIEWER
}
