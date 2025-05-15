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
import org.example.ProjectProgressReportGenerator;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dialog for project report generation.
 */
public class ProjectReportDialog extends ReportUIBase {

    // Cache for project manager IDs (project_id -> manager_id)
    private Map<Integer, Integer> projectManagersCache = new HashMap<>();

    /**
     * Generates a project progress report.
     * 
     * @param fileName The output file name
     * @param folder The output folder
     * @param statusLabel The label to update with status messages
     */
    public void generateProjectReport(String fileName, File folder, Label statusLabel) {
        Map<String, Integer> projectMap = loadProjects();
        if (projectMap.isEmpty()) {
            statusLabel.setText("Brak projektów.");
            return;
        }

        showProjectMultiSelectionDialog("Wybierz projekty", projectMap, (selectedProjects, status, managerId) -> {
            try {
                if (selectedProjects.isEmpty()) {
                    statusLabel.setText("Nie wybrano projektów.");
                    return;
                }

                ArrayList<Integer> projectIds = new ArrayList<>(selectedProjects.values());
                ProjectProgressReportGenerator.generateMultipleFilteredReport(projectIds, fileName, folder, status, managerId);

                String projectNames = String.join(", ", selectedProjects.keySet());
                statusLabel.setText("Wygenerowano raport postępu dla: " + projectNames);
            } catch (Exception ex) {
                ex.printStackTrace();
                statusLabel.setText("Błąd generowania PDF");
            }
        });
    }

    /**
     * Shows a dialog for selecting multiple projects with filtering options.
     * 
     * @param title The dialog title
     * @param projects The projects to display
     * @param onSelected Callback when projects are selected
     */
    private void showProjectMultiSelectionDialog(String title, Map<String, Integer> projects, 
                                              DialogUtils.ProjectMultiFilterConsumer<Map<String, Integer>> onSelected) {
        // Create a new stage for the dialog
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);
        dialog.setMinWidth(400);
        dialog.setMinHeight(550);

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

        // Create a list view with checkboxes
        ListView<CheckBox> listView = new ListView<>();
        VBox.setVgrow(listView, Priority.ALWAYS);

        // Create observable list and filtered list
        ObservableList<CheckBox> items = FXCollections.observableArrayList();
        FilteredList<CheckBox> filteredItems = new FilteredList<>(items, p -> true);

        // Add all projects to the list
        for (Map.Entry<String, Integer> entry : projects.entrySet()) {
            CheckBox cb = new CheckBox(entry.getKey());
            cb.setUserData(entry.getValue());
            items.add(cb);
        }

        // Load project statuses and manager IDs in advance to reduce database queries
        Map<Integer, String> projectStatuses = new HashMap<>();
        projectManagersCache.clear(); // Clear the cache before populating
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id, status, manager_id FROM Projects")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int projectId = rs.getInt("id");
                    projectStatuses.put(projectId, rs.getString("status"));
                    projectManagersCache.put(projectId, rs.getInt("manager_id"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Set up filtering based on search text, status, and manager
        Runnable updateFilter = () -> {
            String searchText = searchField.getText().toLowerCase();
            String selectedStatus = statusComboBox.getValue();
            String selectedManager = managerComboBox.getValue();

            filteredItems.setPredicate(checkBox -> {
                String projectName = checkBox.getText();
                Integer projectId = (Integer) checkBox.getUserData();

                // If search text is empty and "All" is selected for both filters, show all
                if ((searchText == null || searchText.isEmpty()) && 
                    "Wszystkie".equals(selectedStatus) && 
                    "Wszyscy".equals(selectedManager)) {
                    return true;
                }

                // Check if the project matches the search text
                boolean matchesSearch = searchText == null || searchText.isEmpty() || 
                                       projectName.toLowerCase().contains(searchText);

                // Check if the project matches the selected status
                boolean matchesStatus = "Wszystkie".equals(selectedStatus) || 
                                       selectedStatus.equals(projectStatuses.get(projectId));

                // For manager filtering, check if the selected manager is "Wszyscy" (All)
                // If not, check if the project's manager matches the selected manager
                boolean matchesManager = "Wszyscy".equals(selectedManager);
                if (!matchesManager && projectId != null) {
                    Integer projectManagerId = projectManagersCache.get(projectId);
                    Integer selectedManagerId = managers.get(selectedManager);
                    matchesManager = (selectedManagerId == null) || 
                                  (projectManagerId == selectedManagerId);
                }

                return matchesSearch && matchesStatus && matchesManager;
            });
        };

        // Add listeners to search field and combo boxes
        searchField.textProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());
        statusComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());
        managerComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateFilter.run());

        // Set the items to the list view
        listView.setItems(filteredItems);

        // Create buttons
        Button okButton = new Button("OK");
        Button cancelButton = new Button("Anuluj");

        // Set button actions
        okButton.setOnAction(e -> {
            Map<String, Integer> selectedProjects = new LinkedHashMap<>();
            for (CheckBox cb : items) {
                if (cb.isSelected()) {
                    selectedProjects.put(cb.getText(), (Integer) cb.getUserData());
                }
            }

            String selectedStatus = statusComboBox.getValue();
            String selectedManager = managerComboBox.getValue();

            // Convert status and manager to appropriate values for filtering
            String statusFilter = "Wszystkie".equals(selectedStatus) ? null : selectedStatus;
            Integer managerFilter = "Wszyscy".equals(selectedManager) ? null : managers.get(selectedManager);

            onSelected.accept(selectedProjects, statusFilter, managerFilter);
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
            new Label("Wybierz projekty:"), 
            listView, 
            buttonBox
        );

        // Set the scene
        Scene scene = new Scene(layout);
        dialog.setScene(scene);
        dialog.showAndWait();
    }
}
