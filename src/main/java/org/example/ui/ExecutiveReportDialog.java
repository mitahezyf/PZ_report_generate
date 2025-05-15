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
import org.example.ExecutiveOverviewReportGenerator;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.UnaryOperator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dialog for executive report generation.
 */
public class ExecutiveReportDialog extends ReportUIBase {

    // Cache for project data
    private Map<Integer, Integer> projectManagersCache = new HashMap<>();
    private Map<Integer, String> projectStatusCache = new HashMap<>();
    private Map<Integer, Integer> overdueTasksCache = new HashMap<>();
    private Map<Integer, Integer> overdueMilestonesCache = new HashMap<>();
    private Map<Integer, Double> taskCompletionRateCache = new HashMap<>();

    /**
     * Overrides the loadProjects method to also populate the projectManagersCache.
     * 
     * @return A map of project names to their IDs
     */
    @Override
    protected Map<String, Integer> loadProjects() {
        Map<String, Integer> map = new LinkedHashMap<>();

        // Clear all caches before populating
        projectManagersCache.clear();
        projectStatusCache.clear();
        overdueTasksCache.clear();
        overdueMilestonesCache.clear();
        taskCompletionRateCache.clear();

        try (Connection conn = DatabaseConnector.getConnection()) {
            // First load basic project data
            try (PreparedStatement stmt = conn.prepareStatement("SELECT id, name, manager_id, status FROM Projects");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int projectId = rs.getInt("id");
                    map.put(rs.getString("name"), projectId);
                    projectManagersCache.put(projectId, rs.getInt("manager_id"));
                    projectStatusCache.put(projectId, rs.getString("status"));
                }
            }

            // Then load overdue data and task completion rates from the view in a single query
            try (PreparedStatement stmt = conn.prepareStatement(
                     "SELECT project_id, overdue_tasks, overdue_milestones, task_completion_rate FROM vw_ExecutiveOverview");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int projectId = rs.getInt("project_id");
                    overdueTasksCache.put(projectId, rs.getInt("overdue_tasks"));
                    overdueMilestonesCache.put(projectId, rs.getInt("overdue_milestones"));
                    taskCompletionRateCache.put(projectId, rs.getDouble("task_completion_rate"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return map;
    }

    /**
     * Generates an executive overview report.
     * 
     * @param fileName The output file name
     * @param folder The output folder
     * @param statusLabel The label to update with status messages
     */
    public void generateExecutiveReport(String fileName, File folder, Label statusLabel) {
        Map<String, Integer> projectMap = loadProjects();
        if (projectMap.isEmpty()) {
            statusLabel.setText("Brak projektów.");
            return;
        }

        showExecutiveReportDialog("Wybierz projekt", projectMap, (name, id, status, managerId, 
                                                                showOverdueTasks, showOverdueMilestones,
                                                                minCompletionRate, maxCompletionRate) -> {
            try {
                ExecutiveOverviewReportGenerator.generateFilteredReport(
                    id, fileName, folder, status, managerId,
                    showOverdueTasks, showOverdueMilestones,
                    minCompletionRate, maxCompletionRate
                );
                statusLabel.setText("Wygenerowano raport zarządczy dla: " + name);
            } catch (Exception ex) {
                ex.printStackTrace();
                statusLabel.setText("Błąd generowania PDF");
            }
        });
    }

    /**
     * Shows a dialog for selecting a project with additional executive report filtering options.
     * 
     * @param title The dialog title
     * @param projects The projects to display
     * @param onSelected Callback when a project is selected
     */
    private void showExecutiveReportDialog(String title, Map<String, Integer> projects, 
                                         DialogUtils.ExecutiveReportFilterConsumer<String, Integer> onSelected) {
        // Create a new stage for the dialog
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);
        dialog.setMinWidth(400);
        dialog.setMinHeight(500);

        // Create a search field
        TextField searchField = new TextField();
        searchField.setPromptText("Szukaj projektu...");
        searchField.setPrefWidth(Double.MAX_VALUE);

        // Create status filter
        Label statusLabel = new Label("Filtruj według statusu:");
        ComboBox<String> statusComboBox = new ComboBox<>();
        statusComboBox.getItems().add("Wszystkie");
        statusComboBox.getItems().addAll(loadProjectStatuses());
        statusComboBox.setValue("Wszystkie");
        statusComboBox.setPrefWidth(Double.MAX_VALUE);

        // Create manager filter
        Label managerLabel = new Label("Filtruj według managera:");
        ComboBox<String> managerComboBox = new ComboBox<>();
        Map<String, Integer> managers = loadProjectManagers();
        managerComboBox.getItems().add("Wszyscy");
        managerComboBox.getItems().addAll(managers.keySet());
        managerComboBox.setValue("Wszyscy");
        managerComboBox.setPrefWidth(Double.MAX_VALUE);

        // Create overdue filters
        Label overdueLabel = new Label("Filtruj według opóźnień:");
        CheckBox overdueAllCheckBox = new CheckBox("Pokaż tylko projekty z jakimikolwiek opóźnieniami");
        CheckBox overdueTasksCheckBox = new CheckBox("Pokaż tylko projekty z opóźnionymi zadaniami");
        CheckBox overdueMilestonesCheckBox = new CheckBox("Pokaż tylko projekty z opóźnionymi kamieniami milowymi");

        // Make checkboxes mutually exclusive
        overdueAllCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                overdueTasksCheckBox.setSelected(false);
                overdueMilestonesCheckBox.setSelected(false);
            }
        });

        overdueTasksCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                overdueAllCheckBox.setSelected(false);
                overdueMilestonesCheckBox.setSelected(false);
            }
        });

        overdueMilestonesCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                overdueAllCheckBox.setSelected(false);
                overdueTasksCheckBox.setSelected(false);
            }
        });

        // Create completion rate filter
        Label completionRateLabel = new Label("Filtruj według współczynnika ukończenia zadań (%):");
        TextField minCompletionRateField = new TextField("0");
        minCompletionRateField.setPromptText("Min");
        minCompletionRateField.setPrefWidth(80);

        TextField maxCompletionRateField = new TextField("100");
        maxCompletionRateField.setPromptText("Max");
        maxCompletionRateField.setPrefWidth(80);

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

        minCompletionRateField.setTextFormatter(new TextFormatter<>(filter));
        maxCompletionRateField.setTextFormatter(new TextFormatter<>(filter));

        HBox completionRateBox = new HBox(10);
        completionRateBox.setAlignment(Pos.CENTER_LEFT);
        completionRateBox.getChildren().addAll(
            minCompletionRateField, new Label("-"), maxCompletionRateField
        );

        // Create a list view
        ListView<String> listView = new ListView<>();
        VBox.setVgrow(listView, Priority.ALWAYS);

        // Create observable list and filtered list
        ObservableList<String> items = FXCollections.observableArrayList(projects.keySet());
        FilteredList<String> filteredItems = new FilteredList<>(items, p -> true);

        // Set up filtering based on search text, status, manager, and completion rate
        Runnable updateFilter = () -> {
            String searchText = searchField.getText().toLowerCase();
            String selectedStatus = statusComboBox.getValue();
            String selectedManager = managerComboBox.getValue();
            boolean showOverdueAll = overdueAllCheckBox.isSelected();
            boolean showOverdueTasks = overdueTasksCheckBox.isSelected();
            boolean showOverdueMilestones = overdueMilestonesCheckBox.isSelected();

            // Get min and max completion rate values
            Double minCompletionRate = null;
            Double maxCompletionRate = null;

            try {
                if (!minCompletionRateField.getText().isEmpty()) {
                    minCompletionRate = Double.parseDouble(minCompletionRateField.getText());
                }
            } catch (NumberFormatException ex) {
                // Ignore parsing errors
            }

            try {
                if (!maxCompletionRateField.getText().isEmpty()) {
                    maxCompletionRate = Double.parseDouble(maxCompletionRateField.getText());
                }
            } catch (NumberFormatException ex) {
                // Ignore parsing errors
            }

            // Use final variables for lambda
            final Double finalMinCompletionRate = minCompletionRate;
            final Double finalMaxCompletionRate = maxCompletionRate;

            filteredItems.setPredicate(projectName -> {
                // If search text is empty and no filters are applied, show all
                if ((searchText == null || searchText.isEmpty()) && 
                    "Wszystkie".equals(selectedStatus) && 
                    "Wszyscy".equals(selectedManager) &&
                    !showOverdueAll && !showOverdueTasks && !showOverdueMilestones &&
                    finalMinCompletionRate == null && finalMaxCompletionRate == null) {
                    return true;
                }

                Integer projectId = projects.get(projectName);

                // Check if the project matches the search text
                boolean matchesSearch = searchText == null || searchText.isEmpty() || 
                                       projectName.toLowerCase().contains(searchText);

                // Check if the project matches the selected status using cached data
                boolean matchesStatus = "Wszystkie".equals(selectedStatus);
                if (!matchesStatus && projectId != null) {
                    String projectStatus = projectStatusCache.get(projectId);
                    matchesStatus = selectedStatus.equals(projectStatus);
                }

                // For manager filtering, check if the selected manager is "Wszyscy" (All)
                // If not, check if the project's manager matches the selected manager
                boolean matchesManager = "Wszyscy".equals(selectedManager);
                if (!matchesManager && projectId != null) {
                    Integer projectManagerId = projectManagersCache.get(projectId);
                    Integer selectedManagerId = managers.get(selectedManager);
                    matchesManager = (selectedManagerId == null) || 
                                  (projectManagerId == selectedManagerId);
                }

                // Check if the project has overdue tasks or milestones if those filters are applied using cached data
                boolean matchesOverdue = true;
                if (projectId != null && (showOverdueAll || showOverdueTasks || showOverdueMilestones)) {
                    Integer overdueTasks = overdueTasksCache.get(projectId);
                    Integer overdueMilestones = overdueMilestonesCache.get(projectId);

                    // Default to 0 if not in cache
                    overdueTasks = (overdueTasks != null) ? overdueTasks : 0;
                    overdueMilestones = (overdueMilestones != null) ? overdueMilestones : 0;

                    if (showOverdueAll) {
                        // For "all delays", check if either tasks or milestones are overdue
                        if (overdueTasks == 0 && overdueMilestones == 0) {
                            matchesOverdue = false;
                        }
                    } else {
                        if (showOverdueTasks && overdueTasks == 0) {
                            matchesOverdue = false;
                        }

                        if (showOverdueMilestones && overdueMilestones == 0) {
                            matchesOverdue = false;
                        }
                    }
                }

                // Check if the project's completion rate is within the specified range
                boolean matchesCompletionRate = true;
                if (projectId != null && (finalMinCompletionRate != null || finalMaxCompletionRate != null)) {
                    Double completionRate = taskCompletionRateCache.get(projectId);
                    if (completionRate != null) {
                        if (finalMinCompletionRate != null && completionRate < finalMinCompletionRate) {
                            matchesCompletionRate = false;
                        }
                        if (finalMaxCompletionRate != null && completionRate > finalMaxCompletionRate) {
                            matchesCompletionRate = false;
                        }
                    }
                }

                return matchesSearch && matchesStatus && matchesManager && matchesOverdue && matchesCompletionRate;
            });
        };

        // Add listeners to search field, combo boxes, checkboxes, and completion rate fields
        searchField.textProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());
        statusComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());
        managerComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());
        overdueAllCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());
        overdueTasksCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());
        overdueMilestonesCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());
        minCompletionRateField.textProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());
        maxCompletionRateField.textProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());

        // Set the items to the list view
        listView.setItems(filteredItems);

        // Create buttons
        Button okButton = new Button("OK");
        Button cancelButton = new Button("Anuluj");

        // Set button actions
        okButton.setOnAction(e -> {
            String selectedProject = listView.getSelectionModel().getSelectedItem();
            if (selectedProject != null) {
                String selectedStatus = statusComboBox.getValue();
                String selectedManager = managerComboBox.getValue();

                // Convert status and manager to appropriate values for filtering
                String statusFilter = "Wszystkie".equals(selectedStatus) ? null : selectedStatus;
                Integer managerFilter = "Wszyscy".equals(selectedManager) ? null : managers.get(selectedManager);

                // Get completion rate values
                Double minCompletionRate = null;
                Double maxCompletionRate = null;
                boolean hasValidationError = false;

                try {
                    if (!minCompletionRateField.getText().isEmpty()) {
                        double value = Double.parseDouble(minCompletionRateField.getText());
                        if (value >= 0 && value <= 100) {
                            minCompletionRate = value;
                            minCompletionRateField.setStyle("");
                            minCompletionRateField.setTooltip(null);
                        } else {
                            hasValidationError = true;
                            minCompletionRateField.setStyle("-fx-border-color: red;");
                            minCompletionRateField.setTooltip(new Tooltip("Wartość musi być między 0 a 100%"));
                        }
                    }
                } catch (NumberFormatException ex) {
                    hasValidationError = true;
                    minCompletionRateField.setStyle("-fx-border-color: red;");
                    minCompletionRateField.setTooltip(new Tooltip("Wprowadź poprawną wartość liczbową"));
                }

                try {
                    if (!maxCompletionRateField.getText().isEmpty()) {
                        double value = Double.parseDouble(maxCompletionRateField.getText());
                        if (value >= 0 && value <= 100) {
                            maxCompletionRate = value;
                            maxCompletionRateField.setStyle("");
                            maxCompletionRateField.setTooltip(null);
                        } else {
                            hasValidationError = true;
                            maxCompletionRateField.setStyle("-fx-border-color: red;");
                            maxCompletionRateField.setTooltip(new Tooltip("Wartość musi być między 0 a 100%"));
                        }
                    }
                } catch (NumberFormatException ex) {
                    hasValidationError = true;
                    maxCompletionRateField.setStyle("-fx-border-color: red;");
                    maxCompletionRateField.setTooltip(new Tooltip("Wprowadź poprawną wartość liczbową"));
                }

                // Check if min is greater than max
                if (minCompletionRate != null && maxCompletionRate != null && minCompletionRate > maxCompletionRate) {
                    hasValidationError = true;
                    minCompletionRateField.setStyle("-fx-border-color: red;");
                    maxCompletionRateField.setStyle("-fx-border-color: red;");
                    minCompletionRateField.setTooltip(new Tooltip("Wartość minimalna nie może być większa niż maksymalna"));
                    maxCompletionRateField.setTooltip(new Tooltip("Wartość maksymalna nie może być mniejsza niż minimalna"));
                }

                // If there are validation errors, don't proceed
                if (hasValidationError) {
                    return;
                }

                // Determine which overdue filter to use
                boolean showOverdueTasks = overdueTasksCheckBox.isSelected();
                boolean showOverdueMilestones = overdueMilestonesCheckBox.isSelected();
                boolean showOverdueAll = overdueAllCheckBox.isSelected();

                // If "all delays" is selected, pass that instead of individual filters
                boolean effectiveOverdueTasks = showOverdueAll || showOverdueTasks;
                boolean effectiveOverdueMilestones = showOverdueAll || showOverdueMilestones;

                onSelected.accept(selectedProject, projects.get(selectedProject), statusFilter, managerFilter,
                                 effectiveOverdueTasks, effectiveOverdueMilestones,
                                 minCompletionRate, maxCompletionRate);
            }
            dialog.close();
        });

        cancelButton.setOnAction(e -> dialog.close());

        // Create button layout
        HBox buttonBox = new HBox(10, okButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        // Create main layout
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));
        layout.getChildren().addAll(
            new Label("Wyszukaj:"), 
            searchField,
            statusLabel,
            statusComboBox,
            managerLabel,
            managerComboBox,
            overdueLabel,
            overdueAllCheckBox,
            overdueTasksCheckBox,
            overdueMilestonesCheckBox,
            completionRateLabel,
            completionRateBox,
            new Label("Wybierz projekt:"), 
            listView, 
            buttonBox
        );

        // Set the scene
        Scene scene = new Scene(layout);
        dialog.setScene(scene);
        dialog.showAndWait();
    }
}
