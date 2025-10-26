-- Migration V8: Create alerted chats table
--Description: Saving all chats with tg-bot

CREATE TABLE chats (
    id BIGSERIAL PRIMARY KEY,
    chat_id VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    username VARCHAR(255),
    active BOOLEAN DEFAULT TRUE NOT NULL,
    registered_at TIMESTAMP,
    last_notified_at TIMESTAMP
);

CREATE INDEX idx_chats_active ON chats(active);
CREATE INDEX idx_chats_chat_id ON chats(chat_id);
