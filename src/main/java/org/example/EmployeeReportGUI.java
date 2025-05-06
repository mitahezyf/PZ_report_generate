package org.example;

import javax.swing.*;

public class EmployeeReportGUI {
    private static final String DEFAULT_PATH = System.getProperty("user.home") + "/Documents"; // Dodajemy stałą
    private JFrame frame;
    private JComboBox<String> employeeComboBox;
    private JTextField pathField;

    public EmployeeReportGUI(String[] employees, String defaultPath) {
        this.frame = new JFrame("Wybór pracownika i ścieżki zapisu");
        this.employeeComboBox = new JComboBox<>(employees);
        this.pathField = new JTextField(defaultPath);  // Teraz jest zdefiniowana

        // Ustawienia okna i komponentów
        frame.setSize(400, 250);  // Ustawienie rozmiaru okna
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);  // Centrowanie okna na ekranie

        // Dodanie komponentów do okna
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.add(new JLabel("Wybierz pracownika:"));
        panel.add(employeeComboBox);
        panel.add(new JLabel("Ścieżka do zapisu:"));
        panel.add(pathField);

        frame.add(panel);
    }

    public void show() {
        frame.setVisible(true);  // Pokazanie okna
    }
}
