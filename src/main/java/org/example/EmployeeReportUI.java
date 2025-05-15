package org.example;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

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
        URL cssUrl = EmployeeReportUI.class.getResource("/styles/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("styles.css not found in resources!");
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

    private void generateProjectReport(String fileName, File folder, String type, Label statusLabel) {
        Map<String, Integer> projectMap = loadProjects();
        if (projectMap.isEmpty()) {
            statusLabel.setText("Brak projektów.");
            return;
        }

        if (type.equals("Raport postępu projektu")) {
            showProjectMultiSelectionDialog("Wybierz projekty", projectMap, (selectedProjects, status, managerId) -> {
                try {
                    if (selectedProjects.isEmpty()) {
                        statusLabel.setText("Nie wybrano projektów.");
                        return;
                    }

                    List<Integer> projectIds = new ArrayList<>(selectedProjects.values());
                    ProjectProgressReportGenerator.generateMultipleFilteredReport(projectIds, fileName, folder, status, managerId);

                    String projectNames = String.join(", ", selectedProjects.keySet());
                    statusLabel.setText("Wygenerowano raport postępu dla: " + projectNames);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    statusLabel.setText("Błąd generowania PDF");
                }
            });
        } else {
            showSelectionDialog("Wybierz projekt", "Projekt:", projectMap, (name, id) -> {
                try {
                    ExecutiveOverviewReportGenerator.generateReport(id, fileName, folder);
                    statusLabel.setText("Wygenerowano raport zarządczy dla: " + name);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    statusLabel.setText("Błąd generowania PDF");
                }
            });
        }
    }

    private Map<String, Integer> loadEmployees() {
        return loadEmployeesByRole(null);
    }

    private Map<String, Integer> loadEmployeesByRole(Integer roleId) {
        Map<String, Integer> map = new LinkedHashMap<>();
        try (Connection conn = DatabaseConnector.getConnection()) {
            String sql = "SELECT u.id, CONCAT(u.first_name, ' ', u.last_name) AS name, r.name AS role, r.id AS role_id " +
                         "FROM Users u JOIN Roles r ON u.role_id = r.id";

            if (roleId != null) {
                sql += " WHERE r.id = ?";
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (roleId != null) {
                    stmt.setInt(1, roleId);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("name");
                        String userRole = rs.getString("role");
                        int userRoleId = rs.getInt("role_id");
                        map.put(name + " (" + userRole + ")", rs.getInt("id"));
                    }
                }
            }
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

    private List<String> loadProjectStatuses() {
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

    private Map<String, Integer> loadProjectManagers() {
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


    private void showSelectionDialog(String title, String label, Map<String, Integer> options,
                                     BiConsumer<String, Integer> onSelected) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(options.keySet().iterator().next(), options.keySet());
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.setContentText(label);
        dialog.showAndWait().ifPresent(name -> onSelected.accept(name, options.get(name)));
    }

    private void showProjectMultiSelectionDialog(String title, Map<String, Integer> projects, 
                                              ProjectMultiFilterConsumer<Map<String, Integer>> onSelected) {
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

        // Load project statuses in advance to reduce database queries
        Map<Integer, String> projectStatuses = new HashMap<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id, status FROM Projects")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    projectStatuses.put(rs.getInt("id"), rs.getString("status"));
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

                // For manager filtering, we need to check if the selected manager is "Wszyscy" (All)
                // If not, we'll filter at the database level when the report is generated
                // For now, we'll show all projects regardless of the selected manager
                boolean matchesManager = true;

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

    private void showProjectSelectionDialog(String title, Map<String, Integer> projects, 
                                           ProjectFilterConsumer<String, Integer> onSelected) {
        // Create a new stage for the dialog
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);
        dialog.setMinWidth(400);
        dialog.setMinHeight(400);

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

        // Create a list view
        ListView<String> listView = new ListView<>();
        VBox.setVgrow(listView, Priority.ALWAYS);

        // Create observable list and filtered list
        ObservableList<String> items = FXCollections.observableArrayList(projects.keySet());
        FilteredList<String> filteredItems = new FilteredList<>(items, p -> true);

        // Set up filtering based on search text, status, and manager
        Runnable updateFilter = () -> {
            String searchText = searchField.getText().toLowerCase();
            String selectedStatus = statusComboBox.getValue();
            String selectedManager = managerComboBox.getValue();

            filteredItems.setPredicate(projectName -> {
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
                boolean matchesStatus = "Wszystkie".equals(selectedStatus);
                if (!matchesStatus) {
                    try (Connection conn = DatabaseConnector.getConnection();
                         PreparedStatement stmt = conn.prepareStatement(
                             "SELECT status FROM Projects WHERE id = ?")) {
                        stmt.setInt(1, projects.get(projectName));
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                matchesStatus = selectedStatus.equals(rs.getString("status"));
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }

                // For manager filtering, we need to check if the selected manager is "Wszyscy" (All)
                // If not, we'll filter at the database level when the report is generated
                // For now, we'll show all projects regardless of the selected manager
                boolean matchesManager = true;

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
            String selectedProject = listView.getSelectionModel().getSelectedItem();
            if (selectedProject != null) {
                String selectedStatus = statusComboBox.getValue();
                String selectedManager = managerComboBox.getValue();

                // Convert status and manager to appropriate values for filtering
                String statusFilter = "Wszystkie".equals(selectedStatus) ? null : selectedStatus;
                Integer managerFilter = "Wszyscy".equals(selectedManager) ? null : managers.get(selectedManager);

                onSelected.accept(selectedProject, projects.get(selectedProject), statusFilter, managerFilter);
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
            new Label("Wybierz projekt:"), 
            listView, 
            buttonBox
        );

        // Set the scene
        Scene scene = new Scene(layout);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private Map<String, Integer> loadRoles() {
        Map<String, Integer> roles = new LinkedHashMap<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id, name FROM Roles");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                roles.put(rs.getString("name"), rs.getInt("id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return roles;
    }

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
                                         PerformanceMapConsumer<Map<String, Integer>> onSelected) {
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

        // Only allow numeric input with optional decimal point
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
                    if (value <= 100) {
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
        Map<String, Integer> roles = loadRoles();
        Map<String, CheckBox> roleCheckboxes = new HashMap<>();

        HBox roleFilterBox = new HBox(10);
        roleFilterBox.setAlignment(Pos.CENTER_LEFT);

        for (Map.Entry<String, Integer> role : roles.entrySet()) {
            CheckBox cb = new CheckBox(role.getKey());
            cb.setSelected(true); // All roles selected by default
            cb.setUserData(role.getValue()); // Store role ID as user data
            roleCheckboxes.put(role.getKey(), cb);
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
                // If no search text, all roles selected, and no performance range, show all
                if ((searchText == null || searchText.isEmpty()) && 
                    roleCheckboxes.values().stream().allMatch(CheckBox::isSelected) &&
                    finalMinPerformance == null && finalMaxPerformance == null) {
                    return true;
                }

                String itemText = checkBox.getText().toLowerCase();
                Integer userId = (Integer) checkBox.getUserData();

                // Check if the item matches the search text
                boolean matchesSearch = searchText == null || searchText.isEmpty() || 
                                       itemText.contains(searchText.toLowerCase());

                // Check if the item's role is selected
                boolean matchesRole = false;

                // Extract the role from the item text (format: "name (role)")
                String itemRole = "";
                int startIndex = itemText.lastIndexOf('(');
                int endIndex = itemText.lastIndexOf(')');
                if (startIndex >= 0 && endIndex > startIndex) {
                    itemRole = itemText.substring(startIndex + 1, endIndex).trim();
                    System.out.println("Extracted role: " + itemRole);
                }

                // Get the role ID for this item from the database
                Integer itemRoleId = null;
                try (Connection conn = DatabaseConnector.getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT id FROM Roles WHERE name = ?")) {
                    stmt.setString(1, itemRole);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            itemRoleId = rs.getInt("id");
                            System.out.println("Item role ID: " + itemRoleId);
                        } else {
                            System.out.println("No role ID found for: " + itemRole);
                            // Try with common variations
                            if (itemRole.equalsIgnoreCase("projektmanager") || 
                                itemRole.equalsIgnoreCase("projekt manager") || 
                                itemRole.equalsIgnoreCase("manager")) {
                                stmt.setString(1, "projektManager");
                            } else if (itemRole.equalsIgnoreCase("teamlider") || 
                                       itemRole.equalsIgnoreCase("team lider") || 
                                       itemRole.equalsIgnoreCase("lider")) {
                                stmt.setString(1, "teamLider");
                            } else {
                                return false; // No match found
                            }

                            try (ResultSet rs2 = stmt.executeQuery()) {
                                if (rs2.next()) {
                                    itemRoleId = rs2.getInt("id");
                                    System.out.println("Item role ID found with alternative name: " + itemRoleId);
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                for (Map.Entry<String, CheckBox> roleEntry : roleCheckboxes.entrySet()) {
                    if (roleEntry.getValue().isSelected()) {
                        Integer roleId = (Integer) roleEntry.getValue().getUserData();
                        System.out.println("Role ID: " + roleId + ", Item role ID: " + itemRoleId);

                        // Compare role IDs
                        if (itemRoleId != null && roleId != null && itemRoleId.equals(roleId)) {
                            matchesRole = true;
                            System.out.println("Match found!");
                            break;
                        }
                    }
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

    public static void main(String[] args) {
        launch(args);
    }

    @FunctionalInterface
    interface BiConsumer<K, V> {
        void accept(K k, V v);
    }

    @FunctionalInterface
    interface MapConsumer<T> {
        void accept(T t);
    }

    @FunctionalInterface
    interface PerformanceMapConsumer<T> {
        void accept(T t, Double minPerformance, Double maxPerformance);
    }

    @FunctionalInterface
    interface ProjectFilterConsumer<K, V> {
        void accept(K name, V id, String status, Integer managerId);
    }

    @FunctionalInterface
    interface ProjectMultiFilterConsumer<T> {
        void accept(T selectedProjects, String status, Integer managerId);
    }
}
