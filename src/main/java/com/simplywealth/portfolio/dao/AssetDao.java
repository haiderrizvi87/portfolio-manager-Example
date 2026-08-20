package com.simplywealth.portfolio.dao;

import com.simplywealth.portfolio.config.DatabaseConfig;
import com.simplywealth.portfolio.model.Asset;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/** Raw JDBC access to the Asset table - no ORM, per NFR8. Plain if/else logic, no lambdas (cohort syntax rule). */
public class AssetDao {

    public Optional<Asset> findByTicker(String ticker) throws SQLException {
        String sql = "SELECT id, ticker, asset_type, name FROM Asset WHERE ticker = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ticker);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public Optional<Asset> findById(Long id) throws SQLException {
        String sql = "SELECT id, ticker, asset_type, name FROM Asset WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /** Inserts a new Asset row and returns it with its generated id. */
    public Asset insert(Asset asset) throws SQLException {
        String sql = "INSERT INTO Asset (ticker, asset_type, name) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, asset.getTicker());
            stmt.setString(2, asset.getAssetType());
            stmt.setString(3, asset.getName());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    asset.setId(keys.getLong(1));
                }
            }
            return asset;
        }
    }

    private Asset mapRow(ResultSet rs) throws SQLException {
        return new Asset(
                rs.getLong("id"),
                rs.getString("ticker"),
                rs.getString("asset_type"),
                rs.getString("name")
        );
    }
}
