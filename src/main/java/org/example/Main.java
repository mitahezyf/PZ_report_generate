package org.example;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class Main {

    private static final String DB_URL = "jdbc:mysql://mysql-pz-programowanie-zespolowe.j.aivencloud.com:23083/pzdb?ssl-mode=REQUIRED";
    private static final String DB_USERNAME = "avnadmin";
    private static final String DB_PASSWORD = "AVNS_xldj6Pywht7u1kl_kgh";
    private static final String DEFAULT_PATH = System.getProperty("user.home") + "/Documents"; // Domyślna ścieżka zapisu

    public static void main(String[] args) {
        // Uruchomienie GUI w wątku Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            // Tworzymy okno GUI
            JFrame frame = createMainFrame();
            frame.setVisible(true);
        });
    }

    private static JFrame createMainFrame() {
        JFrame frame = new JFrame("Wybór raportu i konfiguracja");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 400);

        // Tworzymy panel do trzymania komponentów
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // ComboBox do wyboru rodzaju raportu
        String[] reportTypes = {"Wydajność Pracownika", "Postępy Projektu", "Podsumowanie Wykonania Zarządu"};
        JComboBox<String> reportComboBox = new JComboBox<>(reportTypes);
        reportComboBox.setPreferredSize(new Dimension(300, 30));
        panel.add(new JLabel("Wybierz rodzaj raportu:"));
        panel.add(reportComboBox);

        // Okno z konfiguracją raportu
        JPanel reportConfigPanel = new JPanel();
        reportConfigPanel.setLayout(new BoxLayout(reportConfigPanel, BoxLayout.Y_AXIS));
        reportConfigPanel.setVisible(false); // Początkowo ukryte

        // Przyciski, które pojawią się po wyborze typu raportu
        JButton generateReportButton = new JButton("Generuj raport");
        generateReportButton.setEnabled(false); // Początkowo przycisk jest wyłączony
        reportConfigPanel.add(generateReportButton);

        // ComboBox dla wyboru pracownika (do raportu wydajności pracownika)
        JComboBox<String> employeeComboBox = new JComboBox<>(fetchEmployeeList());
        employeeComboBox.setPreferredSize(new Dimension(300, 30));

        // ComboBox dla wyboru projektu (do raportu postępów projektu)
        JComboBox<String> projectComboBox = new JComboBox<>(fetchProjectList());
        projectComboBox.setPreferredSize(new Dimension(300, 30));

        // ComboBox dla wyboru projektu (do raportu podsumowania wykonania zarządu)
        JComboBox<String> executiveComboBox = new JComboBox<>(fetchProjectList());
        executiveComboBox.setPreferredSize(new Dimension(300, 30));

        // Pole tekstowe dla ścieżki zapisu (początkowo niewidoczne)
        JTextField pathField = new JTextField(DEFAULT_PATH);
        pathField.setEditable(false);
        pathField.setPreferredSize(new Dimension(300, 30));

        // Przycisk do wyboru ścieżki zapisu
        JButton browseButton = new JButton("Wybierz folder");
        browseButton.setEnabled(false);

        // Pole tekstowe do edycji nazwy pliku
        String defaultFileName = getDefaultFileName();
        JTextField fileNameField = new JTextField(defaultFileName);
        fileNameField.setPreferredSize(new Dimension(300, 30));
        fileNameField.setEnabled(false);

        // Obsługa zmiany wyboru w ComboBox
        reportComboBox.addActionListener(e -> {
            String selectedReport = (String) reportComboBox.getSelectedItem();
            reportConfigPanel.removeAll(); // Usuwamy poprzednią konfigurację
            reportConfigPanel.setVisible(true); // Pokazujemy panel po wyborze raportu

            if ("Wydajność Pracownika".equals(selectedReport)) {
                reportConfigPanel.add(new JLabel("Wybierz pracownika:"));
                reportConfigPanel.add(employeeComboBox);
                enablePathSelection(reportConfigPanel, generateReportButton, employeeComboBox, pathField, fileNameField, browseButton, true);
                generateReportButton.addActionListener(e1 -> {
                    String selectedEmployee = (String) employeeComboBox.getSelectedItem();
                    String fileName = fileNameField.getText();
                    String path = pathField.getText();
                    generateEmployeePerformanceReport(selectedEmployee, path, fileName);
                });
            } else if ("Postępy Projektu".equals(selectedReport)) {
                reportConfigPanel.add(new JLabel("Wybierz projekt:"));
                reportConfigPanel.add(projectComboBox);
                enablePathSelection(reportConfigPanel, generateReportButton, projectComboBox, pathField, fileNameField, browseButton, false);
                generateReportButton.addActionListener(e1 -> {
                    String selectedProject = (String) projectComboBox.getSelectedItem();
                    String fileName = fileNameField.getText();
                    String path = pathField.getText();
                    generateProjectProgressReport(selectedProject, path, fileName);
                });
            } else if ("Podsumowanie Wykonania Zarządu".equals(selectedReport)) {
                reportConfigPanel.add(new JLabel("Wybierz projekt:"));
                reportConfigPanel.add(executiveComboBox);
                enablePathSelection(reportConfigPanel, generateReportButton, executiveComboBox, pathField, fileNameField, browseButton, false);
                generateReportButton.addActionListener(e1 -> {
                    String selectedProject = (String) executiveComboBox.getSelectedItem();
                    String fileName = fileNameField.getText();
                    String path = pathField.getText();
                    generateExecutiveOverviewReport(selectedProject, path, fileName);
                });
            }

            reportConfigPanel.add(generateReportButton);
            reportConfigPanel.revalidate();
            reportConfigPanel.repaint();
        });

        panel.add(reportConfigPanel);
        frame.add(panel);
        frame.setLocationRelativeTo(null);  // Centrowanie okna
        return frame;
    }

    private static void enablePathSelection(JPanel reportConfigPanel, JButton generateReportButton, JComboBox<String> comboBox, JTextField pathField, JTextField fileNameField, JButton browseButton, boolean isEmployeeReport) {
        reportConfigPanel.add(new JLabel("Ścieżka zapisu:"));
        reportConfigPanel.add(pathField);

        reportConfigPanel.add(browseButton);
        browseButton.setEnabled(true);
        browseButton.addActionListener(e -> {
            String selectedPath = chooseDirectory();
            if (selectedPath != null) {
                pathField.setText(selectedPath);
            }
        });

        reportConfigPanel.add(new JLabel("Nazwa pliku:"));
        reportConfigPanel.add(fileNameField);
        fileNameField.setEnabled(true);

        generateReportButton.setEnabled(true);

        if (isEmployeeReport) {
            // Dodajemy pole dla pracownika
            reportConfigPanel.add(comboBox);
        } else {
            // Dodajemy pole dla projektu
            reportConfigPanel.add(comboBox);
        }
    }

    private static String getDefaultFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String dateStr = sdf.format(new Date());
        return "Raport_" + dateStr + ".pdf";
    }

    private static String[] fetchEmployeeList() {
        // Pobieranie listy pracowników z bazy danych
        ArrayList<String> employees = new ArrayList<>();
        String query = "SELECT CONCAT(first_name, ' ', last_name) AS employee FROM Users WHERE role_id = (SELECT id FROM Roles WHERE name = 'pracownik')";

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {

            while (resultSet.next()) {
                employees.add(resultSet.getString("employee"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return employees.toArray(new String[0]);
    }

    private static String[] fetchProjectList() {
        // Pobieranie listy projektów z bazy danych
        ArrayList<String> projects = new ArrayList<>();
        String query = "SELECT name FROM Projects";

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {

            while (resultSet.next()) {
                projects.add(resultSet.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return projects.toArray(new String[0]);
    }

    private static String chooseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Wybierz folder do zapisu raportu");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile().getAbsolutePath();
        }
        return null;
    }

    private static void generateEmployeePerformanceReport(String employeeName, String path, String fileName) {
        // Logika generowania raportu wydajności pracownika
        EmployeePerformanceReport report = new EmployeePerformanceReport(DB_URL, DB_USERNAME, DB_PASSWORD);
        String outputFilePath = path + "/" + fileName;
        report.generateReport(outputFilePath, employeeName);  // Przekazujemy dwa argumenty
    }

    private static void generateProjectProgressReport(String projectName, String path, String fileName) {
        // Logika generowania raportu postępów projektu
        ProjectProgressReport progressReport = new ProjectProgressReport(DB_URL, DB_USERNAME, DB_PASSWORD);
        String outputFilePath = path + "/" + fileName;
        progressReport.generateReport(outputFilePath, projectName);  // Przekazujemy nazwę projektu
    }

    private static void generateExecutiveOverviewReport(String projectName, String path, String fileName) {
        // Logika generowania raportu podsumowania wykonania zarządu
        ExecutiveOverviewReport executiveOverviewReport = new ExecutiveOverviewReport(DB_URL, DB_USERNAME, DB_PASSWORD);
        String outputFilePath = path + "/" + fileName;
        executiveOverviewReport.generateReport(outputFilePath, projectName);  // Przekazujemy nazwę projektu
    }
}
