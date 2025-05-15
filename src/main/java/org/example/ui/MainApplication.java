package org.example.ui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;

/**
 * Main application class for the report generator.
 */
public class MainApplication extends Application {
    
    private File selectedDirectory;
    private EmployeeReportDialog employeeReportDialog;
    private ProjectReportDialog projectReportDialog;
    private ExecutiveReportDialog executiveReportDialog;
    
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Generator raportów");
        
        // Initialize dialogs
        employeeReportDialog = new EmployeeReportDialog();
        projectReportDialog = new ProjectReportDialog();
        executiveReportDialog = new ExecutiveReportDialog();
        
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
            employeeReportDialog.chooseOutputFolder(primaryStage, folderLabel);
            selectedDirectory = employeeReportDialog.getSelectedDirectory();
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
                employeeReportDialog.generateEmployeeReport(fileName, selectedDirectory, statusLabel);
            } else if (selectedType.equals("Raport postępu projektu")) {
                projectReportDialog.generateProjectReport(fileName, selectedDirectory, statusLabel);
            } else {
                executiveReportDialog.generateExecutiveReport(fileName, selectedDirectory, statusLabel);
            }
        });

        Scene scene = new Scene(root, 460, 360);
        URL cssUrl = MainApplication.class.getResource("/styles/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("styles.css not found in resources!");
        }

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}