package com.simplywealth.portfolio.config;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Simple JDBC connection helper (spec Section 7.6).
 * No connection pooling library used, per the no-frameworks constraint - a new
 * Connection is opened per request and closed immediately after. This is fine
 * at single-user demo scale (NFR7); a production version would use a pool.
 *
 * EDIT THESE if your local MySQL setup differs.
 */
public class DatabaseConfig {

    private static final String URL = "jdbc:mysql://localhost:3306/portfolio_manager?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASSWORD = "----------"; // CHANGE THIS to your local MySQL root password

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void initializeSchema() throws SQLException {
        String serverUrl = "jdbc:mysql://localhost:3306/?useSSL=false&serverTimezone=UTC";
        try (Connection conn = DriverManager.getConnection(serverUrl, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE IF NOT EXISTS portfolio_manager");
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS Asset (" +
                            "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "ticker VARCHAR(20) NOT NULL UNIQUE," +
                            "asset_type VARCHAR(10) NOT NULL," +
                            "name VARCHAR(255) NOT NULL" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS Holding (" +
                            "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "asset_id BIGINT NOT NULL," +
                            "quantity DECIMAL(20, 8) NOT NULL," +
                            "price_at_acquisition DECIMAL(20, 8) NOT NULL," +
                            "date_acquired DATE NOT NULL," +
                            "FOREIGN KEY (asset_id) REFERENCES Asset(id)" +
                            ")"
            );
        }
    }




























}
