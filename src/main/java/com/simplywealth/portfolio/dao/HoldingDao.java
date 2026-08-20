package com.simplywealth.portfolio.dao;

import com.simplywealth.portfolio.config.DatabaseConfig;
import com.simplywealth.portfolio.model.Holding;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Raw JDBC access to the Holding table - no ORM, per NFR8. Plain if/else logic, no lambdas (cohort syntax rule). */
public class HoldingDao {

    public Holding insert(Holding holding) throws SQLException {
        String sql = "INSERT INTO Holding (asset_id, quantity, price_at_acquisition, date_acquired) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, holding.getAssetId());
            stmt.setBigDecimal(2, holding.getQuantity());
            stmt.setBigDecimal(3, holding.getPriceAtAcquisition());
            stmt.setDate(4, java.sql.Date.valueOf(holding.getDateAcquired()));
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    holding.setId(keys.getLong(1));
                }
            }
            return holding;
        }
    }

    public List<Holding> findAll() throws SQLException {
        String sql = "SELECT id, asset_id, quantity, price_at_acquisition, date_acquired FROM Holding";
        List<Holding> results = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    public List<Holding> findByAssetId(Long assetId) throws SQLException {
        String sql = "SELECT id, asset_id, quantity, price_at_acquisition, date_acquired FROM Holding WHERE asset_id = ?";
        List<Holding> results = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, assetId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        }
        return results;
    }

    public void deleteById(Long id) throws SQLException {
        String sql = "DELETE FROM Holding WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    private Holding mapRow(ResultSet rs) throws SQLException {
        return new Holding(
                rs.getLong("id"),
                rs.getLong("asset_id"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("price_at_acquisition"),
                rs.getDate("date_acquired").toLocalDate()
        );
    }
}
