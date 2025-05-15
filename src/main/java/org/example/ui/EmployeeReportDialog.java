package org.example.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.DatabaseConnector;
import org.example.EmployeePerformanceReportGenerator;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Dialog for employee report generation.
 */
public class EmployeeReportDialog extends ReportUIBase {

    /**
     * Generates an employee performance report.
     * 
     * @param fileName The output file name
     * @param folder The output folder
     * @param statusLabel The label to update with status messages
     */
    public void generateEmployeeReport(String fileName, File folder, Label statusLabel) {
        Map<String, Integer> employeeMap = loadEmployees();
        if (employeeMap.isEmpty()) {
            statusLabel.setText("Brak pracowników.");
            return;
        }

        showMultiSelectionDialog("Wybierz pracowników", employeeMap, (selectedEmployees, minPerformance, maxPerformance) -> {
            try {
                if (selectedEmployees.isEmpty()) {
                    statusLabel.setText("Nie wybrano pracowników.");
                    return;
                }

                List<Integer> userIds = selectedEmployees.values().stream().collect(Collectors.toList());
                EmployeePerformanceReportGenerator.generateMultipleEmployeeReport(userIds, fileName, folder, minPerformance, maxPerformance);

                String employeeNames = String.join(", ", selectedEmployees.keySet());
                statusLabel.setText("Wygenerowano raport dla: " + employeeNames);
            } catch (Exception ex) {
                ex.printStackTrace();
                statusLabel.setText("Błąd generowania PDF");
            }
        });
    }

    /**
     * Shows a dialog for selecting multiple employees with filtering options.
     * 
     * @param title The dialog title
     * @param options The employees to display
     * @param onSelected Callback when employees are selected
     */
    /**
     * Loads employee performance data from the database.
     * 
     * @return A map of user IDs to their completion rates
     */
    private Map<Integer, Double> loadEmployeePerformanceData() {
        Map<Integer, Double> performanceMap = new HashMap<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT user_id, completion_rate FROM vw_EmployeePerformance");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                performanceMap.put(rs.getInt("user_id"), rs.getDouble("completion_rate"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return performanceMap;
    }

    private void showMultiSelectionDialog(String title, Map<String, Integer> options, 
                                         DialogUtils.PerformanceMapConsumer<Map<String, Integer>> onSelected) {
        // Create a new stage for the dialog
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);
        dialog.setMinWidth(400);
        dialog.setMinHeight(550);

        // Load employee performance data
        Map<Integer, Double> employeePerformance = loadEmployeePerformanceData();

        // Create a search field
        TextField searchField = new TextField();
        searchField.setPromptText("Szukaj pracownika...");
        searchField.setPrefWidth(Double.MAX_VALUE);

        // Create performance range filter fields
        Label performanceRangeLabel = new Label("Zakres wydajności (%):");

        TextField minPerformanceField = new TextField("0");
        minPerformanceField.setPromptText("Min");
        minPerformanceField.setPrefWidth(80);

        TextField maxPerformanceField = new TextField("100");
        maxPerformanceField.setPromptText("Max");
        maxPerformanceField.setPrefWidth(80);

        // Only allow numeric input with optional decimal point and validate range 0-100%
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty()) {
                return change;
            }
            try {
                if (newText.matches("^\\d*\\.?\\d*$")) {
                    if (newText.equals(".")) {
                        return change;
                    }
                    double value = Double.parseDouble(newText);
                    if (value >= 0 && value <= 100) {
                        return change;
                    } else {
                        // Don't reject the change, but show an error message
                        TextField field = (TextField) change.getControl();
                        field.setStyle("-fx-border-color: red;");
                        field.setTooltip(new Tooltip("Wartość musi być między 0 a 100%"));
                        // We still return the change to allow typing, but mark it as invalid
                        return change;
                    }
                }
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            return null;
        };

        minPerformanceField.setTextFormatter(new TextFormatter<>(filter));
        maxPerformanceField.setTextFormatter(new TextFormatter<>(filter));

        HBox performanceRangeBox = new HBox(10);
        performanceRangeBox.setAlignment(Pos.CENTER_LEFT);
        performanceRangeBox.getChildren().addAll(
            minPerformanceField, new Label("-"), maxPerformanceField
        );

        // Create role filter checkboxes
        List<String> roles = loadRoles();
        Map<String, CheckBox> roleCheckboxes = new HashMap<>();

        HBox roleFilterBox = new HBox(10);
        roleFilterBox.setAlignment(Pos.CENTER_LEFT);

        for (String role : roles) {
            CheckBox cb = new CheckBox(role);
            cb.setSelected(true); // All roles selected by default
            roleCheckboxes.put(role, cb);
            roleFilterBox.getChildren().add(cb);
        }

        // Create a list view with checkboxes
        ListView<CheckBox> listView = new ListView<>();
        VBox.setVgrow(listView, Priority.ALWAYS);

        // Create observable list and filtered list
        ObservableList<CheckBox> items = FXCollections.observableArrayList();
        FilteredList<CheckBox> filteredItems = new FilteredList<>(items, p -> true);

        // Add all employees to the list
        for (Map.Entry<String, Integer> entry : options.entrySet()) {
            CheckBox cb = new CheckBox(entry.getKey());
            cb.setUserData(entry.getValue());
            items.add(cb);
        }

        // Set up filtering based on search text, role selection, and performance range
        Runnable updateFilter = () -> {
            String searchText = searchField.getText();

            // Get min and max performance values
            Double minPerformance = null;
            Double maxPerformance = null;

            try {
                if (!minPerformanceField.getText().isEmpty()) {
                    minPerformance = Double.parseDouble(minPerformanceField.getText());
                }
            } catch (NumberFormatException ex) {
                // Ignore parsing errors
            }

            try {
                if (!maxPerformanceField.getText().isEmpty()) {
                    maxPerformance = Double.parseDouble(maxPerformanceField.getText());
                }
            } catch (NumberFormatException ex) {
                // Ignore parsing errors
            }

            // Use final variables for lambda
            final Double finalMinPerformance = minPerformance;
            final Double finalMaxPerformance = maxPerformance;

            filteredItems.setPredicate(checkBox -> {
                String itemText = checkBox.getText().toLowerCase();
                Integer userId = (Integer) checkBox.getUserData();

                // Check if the item matches the search text
                boolean matchesSearch = searchText == null || searchText.isEmpty() || 
                                       itemText.contains(searchText.toLowerCase());

                // Check if the item's role is selected
                boolean matchesRole = false;
                for (Map.Entry<String, CheckBox> roleEntry : roleCheckboxes.entrySet()) {
                    if (roleEntry.getValue().isSelected() && 
                        itemText.contains("(" + roleEntry.getKey().toLowerCase() + ")")) {
                        matchesRole = true;
                        break;
                    }
                }

                // If no roles are selected, make the list empty
                if (roleCheckboxes.values().stream().noneMatch(CheckBox::isSelected)) {
                    matchesRole = false;
                }

                // Check if the employee's performance is within the specified range
                boolean matchesPerformance = true;
                if (userId != null && (finalMinPerformance != null || finalMaxPerformance != null)) {
                    Double performance = employeePerformance.get(userId);
                    if (performance != null) {
                        if (finalMinPerformance != null && performance < finalMinPerformance) {
                            matchesPerformance = false;
                        }
                        if (finalMaxPerformance != null && performance > finalMaxPerformance) {
                            matchesPerformance = false;
                        }
                    }
                }

                return matchesSearch && matchesRole && matchesPerformance;
            });
        };

        // Add listeners to search field, role checkboxes, and performance fields
        searchField.textProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());

        for (CheckBox roleCb : roleCheckboxes.values()) {
            roleCb.selectedProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());
        }

        minPerformanceField.textProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());
        maxPerformanceField.textProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());

        // Set the items to the list view
        listView.setItems(filteredItems);

        // Apply initial filter
        updateFilter.run();

        // Create buttons
        Button selectAllButton = new Button("Zaznacz wszystkie");
        Button clearAllButton = new Button("Odznacz wszystkie");
        Button okButton = new Button("OK");
        Button cancelButton = new Button("Anuluj");

        // Set button actions
        selectAllButton.setOnAction(e -> {
            for (CheckBox cb : filteredItems) {
                cb.setSelected(true);
            }
        });

        clearAllButton.setOnAction(e -> {
            for (CheckBox cb : filteredItems) {
                cb.setSelected(false);
            }
        });

        okButton.setOnAction(e -> {
            Map<String, Integer> selectedEmployees = new LinkedHashMap<>();
            for (CheckBox cb : items) {
                if (cb.isSelected()) {
                    selectedEmployees.put(cb.getText(), (Integer) cb.getUserData());
                }
            }

            // Get min and max performance values
            Double minPerformance = null;
            Double maxPerformance = null;
            boolean hasValidationError = false;

            try {
                if (!minPerformanceField.getText().isEmpty()) {
                    double value = Double.parseDouble(minPerformanceField.getText());
                    if (value >= 0 && value <= 100) {
                        minPerformance = value;
                        minPerformanceField.setStyle("");
                        minPerformanceField.setTooltip(null);
                    } else {
                        hasValidationError = true;
                        minPerformanceField.setStyle("-fx-border-color: red;");
                        minPerformanceField.setTooltip(new Tooltip("Wartość musi być między 0 a 100%"));
                    }
                }
            } catch (NumberFormatException ex) {
                hasValidationError = true;
                minPerformanceField.setStyle("-fx-border-color: red;");
                minPerformanceField.setTooltip(new Tooltip("Wprowadź poprawną wartość liczbową"));
            }

            try {
                if (!maxPerformanceField.getText().isEmpty()) {
                    double value = Double.parseDouble(maxPerformanceField.getText());
                    if (value >= 0 && value <= 100) {
                        maxPerformance = value;
                        maxPerformanceField.setStyle("");
                        maxPerformanceField.setTooltip(null);
                    } else {
                        hasValidationError = true;
                        maxPerformanceField.setStyle("-fx-border-color: red;");
                        maxPerformanceField.setTooltip(new Tooltip("Wartość musi być między 0 a 100%"));
                    }
                }
            } catch (NumberFormatException ex) {
                hasValidationError = true;
                maxPerformanceField.setStyle("-fx-border-color: red;");
                maxPerformanceField.setTooltip(new Tooltip("Wprowadź poprawną wartość liczbową"));
            }

            // Check if min is greater than max
            if (minPerformance != null && maxPerformance != null && minPerformance > maxPerformance) {
                hasValidationError = true;
                minPerformanceField.setStyle("-fx-border-color: red;");
                maxPerformanceField.setStyle("-fx-border-color: red;");
                minPerformanceField.setTooltip(new Tooltip("Wartość minimalna nie może być większa niż maksymalna"));
                maxPerformanceField.setTooltip(new Tooltip("Wartość maksymalna nie może być mniejsza niż minimalna"));
            }

            // If there are validation errors, don't proceed
            if (hasValidationError) {
                return;
            }

            onSelected.accept(selectedEmployees, minPerformance, maxPerformance);
            dialog.close();
        });

        cancelButton.setOnAction(e -> dialog.close());

        // Create button layout
        HBox selectionButtons = new HBox(10, selectAllButton, clearAllButton);
        selectionButtons.setAlignment(Pos.CENTER_LEFT);

        HBox actionButtons = new HBox(10, okButton, cancelButton);
        actionButtons.setAlignment(Pos.CENTER_RIGHT);

        BorderPane buttonPane = new BorderPane();
        buttonPane.setLeft(selectionButtons);
        buttonPane.setRight(actionButtons);

        // Create main layout
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));
        layout.getChildren().addAll(
                new Label("Wyszukaj:"), 
                searchField,
                new Label("Filtruj według roli:"),
                roleFilterBox,
                performanceRangeLabel,
                performanceRangeBox,
                new Label("Wybierz pracowników:"), 
                listView, 
                buttonPane
        );

        // Set the scene
        Scene scene = new Scene(layout);
        dialog.setScene(scene);
        dialog.showAndWait();
    }
}
