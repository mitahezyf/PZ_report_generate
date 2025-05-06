package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DatabaseManager {
    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;

    public DatabaseManager(String dbUrl, String dbUsername, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUsername = dbUsername;
        this.dbPassword = dbPassword;
    }

    public String[] fetchEmployeeList() {
        String[] employees = new String[0];  // Default empty list
        String sql = "SELECT CONCAT(first_name, ' ', last_name) AS employee FROM Users WHERE role_id = (SELECT id FROM Roles WHERE name = 'pracownik')";

        try (Connection connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
             PreparedStatement statement = connection.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
             ResultSet resultSet = statement.executeQuery()) {

            // Get the number of employees
            int size = 0;
            while (resultSet.next()) {
                size++;
            }

            if (size == 0) {
                System.out.println("Brak pracowników w bazie danych.");
                return new String[0];  // Return an empty array if no employees are found
            }

            // Create the array with the correct size
            employees = new String[size];
            resultSet.beforeFirst();  // Move the cursor to the beginning

            // Fill the array with employee names
            int i = 0;
            while (resultSet.next()) {
                employees[i++] = resultSet.getString("employee");
            }

        } catch (Exception e) {
            e.printStackTrace();  // Print stack trace to debug any exceptions
        }

        return employees;
    }
}
