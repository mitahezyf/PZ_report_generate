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
        primaryStage.setTitle("Generowanie raportu");

        Label statusLabel = new Label();

        List<String> reportTypes = List.of("Raport wydajności pracownika", "Raport postępu projektu", "Raport zarządczy projektu");
        ChoiceBox<String> reportTypeBox = new ChoiceBox<>();
        reportTypeBox.getItems().addAll(reportTypes);
        reportTypeBox.setValue(reportTypes.get(0));

        Button continueButton = new Button("Dalej");

        continueButton.setOnAction(e -> {
            String selectedType = reportTypeBox.getValue();
            if (selectedType.equals("Raport wydajności pracownika")) {
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
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    statusLabel.setText("Błąd połączenia z bazą danych");
                    return;
                }

                List<String> employeeNames = new ArrayList<>(employeeMap.keySet());
                if (employeeNames.isEmpty()) {
                    statusLabel.setText("Brak dostępnych pracowników.");
                    return;
                }

                ChoiceDialog<String> dialog = new ChoiceDialog<>(employeeNames.get(0), employeeNames);
                dialog.setTitle("Wybierz pracownika");
                dialog.setHeaderText("Generowanie raportu wydajności");
                dialog.setContentText("Pracownik:");

                dialog.showAndWait().ifPresent(selectedName -> {
                    int userId = employeeMap.get(selectedName);
                    try {
                        EmployeePerformanceReportGenerator.generateReportFiltered(userId);
                        statusLabel.setText("Wygenerowano raport dla: " + selectedName);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        statusLabel.setText("Błąd generowania PDF");
                    }
                });
            } else {
                Map<String, Integer> projectMap = new LinkedHashMap<>();
                try (Connection conn = DatabaseConnector.getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT id, name FROM Projects");
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        String name = rs.getString("name");
                        int id = rs.getInt("id");
                        projectMap.put(name, id);
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    statusLabel.setText("Błąd połączenia z bazą danych");
                    return;
                }

                List<String> projectNames = new ArrayList<>(projectMap.keySet());
                if (projectNames.isEmpty()) {
                    statusLabel.setText("Brak dostępnych projektów.");
                    return;
                }

                ChoiceDialog<String> dialog = new ChoiceDialog<>(projectNames.get(0), projectNames);
                dialog.setTitle("Wybierz projekt");
                dialog.setHeaderText("Generowanie raportu");
                dialog.setContentText("Projekt:");

                dialog.showAndWait().ifPresent(selectedName -> {
                    int projectId = projectMap.get(selectedName);
                    try {
                        if (selectedType.equals("Raport postępu projektu")) {
                            ProjectProgressReportGenerator.generateReport(projectId);
                            statusLabel.setText("Wygenerowano raport postępu dla projektu: " + selectedName);
                        } else {
                            ExecutiveOverviewReportGenerator.generateReport(projectId);
                            statusLabel.setText("Wygenerowano raport zarządczy dla projektu: " + selectedName);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        statusLabel.setText("Błąd generowania PDF");
                    }
                });
            }
        });

        VBox layout = new VBox(15);
        layout.getChildren().addAll(new Label("Wybierz typ raportu:"), reportTypeBox, continueButton, statusLabel);

        primaryStage.setScene(new Scene(layout, 400, 200));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
