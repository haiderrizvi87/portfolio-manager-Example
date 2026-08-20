-- Portfolio Manager schema (MySQL)
-- Run this once against your local MySQL server before starting the app:
--   mysql -u root -p < schema.sql

CREATE DATABASE IF NOT EXISTS portfolio_manager;
USE portfolio_manager;

CREATE TABLE IF NOT EXISTS Asset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL UNIQUE,
    asset_type VARCHAR(10) NOT NULL, -- 'stock' | 'etf' | 'crypto'
    name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS Holding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    quantity DECIMAL(20, 8) NOT NULL,
    price_at_acquisition DECIMAL(20, 8) NOT NULL,
    date_acquired DATE NOT NULL,
    FOREIGN KEY (asset_id) REFERENCES Asset(id)
);
