package org.example.ui;

import javafx.scene.control.Label;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.example.DatabaseConnector;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for report UI components with common functionality.
 */
public class ReportUIBase {
    protected File selectedDirectory;

    /**
     * Initializes the selected directory to the user's Documents folder.
     */
    protected void initializeSelectedDirectory() {
        selectedDirectory = new File(System.getProperty("user.home"), "Documents");
    }

    /**
     * Gets the currently selected directory.
     * 
     * @return The selected directory
     */
    public File getSelectedDirectory() {
        return selectedDirectory;
    }

    /**
     * Shows a directory chooser dialog to select the output folder.
     * 
     * @param stage The parent stage
     * @param folderLabel The label to update with the selected path
     */
    protected void chooseOutputFolder(Stage stage, Label folderLabel) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Wybierz folder zapisu");
        File chosen = chooser.showDialog(stage);
        if (chosen != null) {
            selectedDirectory = chosen;
            folderLabel.setText(chosen.getAbsolutePath());
        }
    }

    /**
     * Loads all employees from the database.
     * 
     * @return A map of employee names to their IDs
     */
    protected Map<String, Integer> loadEmployees() {
        return loadEmployeesByRole(null);
    }

    /**
     * Loads employees with a specific role from the database.
     * 
     * @param role The role to filter by, or null for all roles
     * @return A map of employee names to their IDs
     */
    protected Map<String, Integer> loadEmployeesByRole(String role) {
        Map<String, Integer> map = new LinkedHashMap<>();
        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = "SELECT u.id, CONCAT(u.first_name, ' ', u.last_name) AS name, r.name AS role " +
                         "FROM Users u JOIN Roles r ON u.role_id = r.id";

            if (role != null && !role.isEmpty()) {
                sql += " WHERE r.name = ?";
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (role != null && !role.isEmpty()) {
                    stmt.setString(1, role);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("name");
                        String dbRole = rs.getString("role");
                        String translatedRole = translateRoleName(dbRole);
                        map.put(name + " (" + translatedRole + ")", rs.getInt("id"));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    /**
     * Loads all projects from the database.
     * 
     * @return A map of project names to their IDs
     */
    protected Map<String, Integer> loadProjects() {
        Map<String, Integer> map = new LinkedHashMap<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id, name FROM Projects");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) map.put(rs.getString("name"), rs.getInt("id"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    /**
     * Loads all distinct project statuses from the database.
     * 
     * @return A list of project statuses
     */
    protected List<String> loadProjectStatuses() {
        List<String> statuses = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT DISTINCT status FROM Projects");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                statuses.add(rs.getString("status"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return statuses;
    }

    /**
     * Loads all project managers from the database.
     * 
     * @return A map of manager names to their IDs
     */
    protected Map<String, Integer> loadProjectManagers() {
        Map<String, Integer> managers = new LinkedHashMap<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT u.id, CONCAT(u.first_name, ' ', u.last_name) AS name " +
                 "FROM Users u " +
                 "JOIN Roles r ON u.role_id = r.id " +
                 "WHERE r.name = 'projektManager'");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                managers.put(rs.getString("name"), rs.getInt("id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return managers;
    }

    /**
     * Loads all roles from the database and translates them to user-friendly format.
     * 
     * @return A list of translated role names
     */
    protected List<String> loadRoles() {
        List<String> roles = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT name FROM Roles");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String dbRole = rs.getString("name");
                String translatedRole = translateRoleName(dbRole);
                roles.add(translatedRole);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return roles;
    }

    /**
     * Translates database role names to user-friendly format.
     * 
     * @param dbRole The role name from the database
     * @return The translated role name
     */
    protected String translateRoleName(String dbRole) {
        switch (dbRole) {
            case "teamLider":
                return "Team Lider";
            case "projektManager":
                return "Projekt Manager";
            case "pracownik":
                return "Pracownik";
            case "prezes":
                return "Prezes";
            default:
                return dbRole;
        }
    }
}
