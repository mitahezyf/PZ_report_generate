package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.*;
import java.util.*;



public class EmployeeReportUI extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Raport Wydajności Pracownika");

        Button generateButton = new Button("Wybierz pracownika i generuj PDF");
        Label statusLabel = new Label();

        Map<String, Integer> employeeMap = new LinkedHashMap<>();

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT u.id, CONCAT(u.first_name, ' ', u.last_name) AS name " +
                             "FROM Users u JOIN Roles r ON u.role_id = r.id WHERE r.name = 'pracownik'");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String name = rs.getString("name");
                int id = rs.getInt("id");
                employeeMap.put(name, id);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            statusLabel.setText("Błąd połączenia z bazą danych");
        }

        generateButton.setOnAction(e -> {
            List<String> employeeNames = new ArrayList<>(employeeMap.keySet());

            if (employeeNames.isEmpty()) {
                statusLabel.setText("Brak dostępnych pracowników.");
                return;
            }

            ChoiceDialog<String> dialog = new ChoiceDialog<>(employeeNames.get(0), employeeNames);
            dialog.setTitle("Wybierz pracownika");
            dialog.setHeaderText("Generowanie raportu PDF");
            dialog.setContentText("Wybierz pracownika:");

            Optional<String> result = dialog.showAndWait();

            result.ifPresent(selectedName -> {
                int userId = employeeMap.get(selectedName);
                try {
                    EmployeePerformanceReportGenerator.generateReportFiltered("employee_report_user_" + userId + ".pdf", userId);
                    statusLabel.setText("Wygenerowano PDF dla: " + selectedName);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    statusLabel.setText("Błąd generowania PDF");
                }
            });
        });

        VBox layout = new VBox(15);
        layout.getChildren().addAll(generateButton, statusLabel);

        primaryStage.setScene(new Scene(layout, 400, 150));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
