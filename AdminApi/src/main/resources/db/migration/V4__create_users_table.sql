-- Create users table for authentication and authorization
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index on username for faster lookups during authentication
CREATE INDEX idx_users_username ON users(username);

-- Comments for clarity
COMMENT ON TABLE users IS 'User accounts for authentication and RBAC authorization';
COMMENT ON COLUMN users.username IS 'Unique username for login';
COMMENT ON COLUMN users.password IS 'BCrypt encrypted password';
COMMENT ON COLUMN users.role IS 'User role: ADMIN (full access) or VIEWER (read-only)';
COMMENT ON COLUMN users.enabled IS 'Account enabled flag';
