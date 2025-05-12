package org.example;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.sql.*;
import java.util.*;

public class EmployeeReportUI extends Application {

    private File selectedDirectory;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Generator raportów");

        Label statusLabel = new Label();

        ChoiceBox<String> reportTypeBox = new ChoiceBox<>();
        reportTypeBox.getItems().addAll(
                "Raport wydajności pracownika",
                "Raport postępu projektu",
                "Raport zarządczy projektu"
        );
        reportTypeBox.setValue("Raport wydajności pracownika");

        TextField fileNameField = new TextField();
        fileNameField.setPromptText("Nazwa pliku (bez rozszerzenia)");

        selectedDirectory = new File(System.getProperty("user.home"), "Documents");
        Label folderLabel = new Label(selectedDirectory.getAbsolutePath());
        Button folderButton = new Button("Wybierz folder");

        folderButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Wybierz folder zapisu");
            File chosen = chooser.showDialog(primaryStage);
            if (chosen != null) {
                selectedDirectory = chosen;
                folderLabel.setText(chosen.getAbsolutePath());
            }
        });

        Button generateButton = new Button("Generuj raport");

        // Styl i rozmieszczenie
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("container");

        root.getChildren().addAll(
                new Label("Typ raportu:"), reportTypeBox,
                new Label("Nazwa pliku:"), fileNameField,
                folderButton, folderLabel,
                generateButton, statusLabel
        );

        generateButton.setOnAction(e -> {
            String selectedType = reportTypeBox.getValue();
            String fileName = fileNameField.getText().trim();
            if (fileName.isEmpty()) fileName = null;

            if (selectedType.equals("Raport wydajności pracownika")) {
                generateEmployeeReport(fileName, selectedDirectory, statusLabel);
            } else {
                generateProjectReport(fileName, selectedDirectory, selectedType, statusLabel);
            }
        });

        Scene scene = new Scene(root, 460, 360);
        URL cssUrl = EmployeeReportUI.class.getResource("/styles/style.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("style.css not found in resources!");
        }

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void generateEmployeeReport(String fileName, File folder, Label statusLabel) {
        Map<String, Integer> employeeMap = loadEmployees();
        if (employeeMap.isEmpty()) {
            statusLabel.setText("Brak pracowników.");
            return;
        }
        showSelectionDialog("Wybierz pracownika", "Pracownik:", employeeMap, (name, id) -> {
            try {
                EmployeePerformanceReportGenerator.generateReportFiltered(id, fileName, folder);
                statusLabel.setText("Wygenerowano raport dla: " + name);
            } catch (Exception ex) {
                ex.printStackTrace();
                statusLabel.setText("Błąd generowania PDF");
            }
        });
    }

    private void generateProjectReport(String fileName, File folder, String type, Label statusLabel) {
        Map<String, Integer> projectMap = loadProjects();
        if (projectMap.isEmpty()) {
            statusLabel.setText("Brak projektów.");
            return;
        }
        showSelectionDialog("Wybierz projekt", "Projekt:", projectMap, (name, id) -> {
            try {
                if (type.equals("Raport postępu projektu")) {
                    ProjectProgressReportGenerator.generateReport(id, fileName, folder);
                    statusLabel.setText("✅ Wygenerowano raport postępu dla: " + name);
                } else {
                    ExecutiveOverviewReportGenerator.generateReport(id, fileName, folder);
                    statusLabel.setText("✅ Wygenerowano raport zarządczy dla: " + name);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                statusLabel.setText("❌ Błąd generowania PDF");
            }
        });
    }

    private Map<String, Integer> loadEmployees() {
        Map<String, Integer> map = new LinkedHashMap<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT u.id, CONCAT(u.first_name, ' ', u.last_name) AS name " +
                             "FROM Users u JOIN Roles r ON u.role_id = r.id WHERE r.name = 'pracownik'");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) map.put(rs.getString("name"), rs.getInt("id"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    private Map<String, Integer> loadProjects() {
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

    private void showSelectionDialog(String title, String label, Map<String, Integer> options,
                                     BiConsumer<String, Integer> onSelected) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(options.keySet().iterator().next(), options.keySet());
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.setContentText(label);
        dialog.showAndWait().ifPresent(name -> onSelected.accept(name, options.get(name)));
    }

    public static void main(String[] args) {
        launch(args);
    }

    @FunctionalInterface
    interface BiConsumer<K, V> {
        void accept(K k, V v);
    }
}
