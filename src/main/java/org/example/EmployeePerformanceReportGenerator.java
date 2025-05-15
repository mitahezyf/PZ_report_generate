package org.example;

import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class EmployeePerformanceReportGenerator {

    public static void generateReportFiltered(int userId, String customFileName, File selectedDirectory) throws SQLException, IOException {
        List<Integer> userIds = List.of(userId);
        generateMultipleEmployeeReport(userIds, customFileName, selectedDirectory, null, null);
    }

    public static void generateMultipleEmployeeReport(List<Integer> userIds, String customFileName, File selectedDirectory) throws SQLException, IOException {
        generateMultipleEmployeeReport(userIds, customFileName, selectedDirectory, null, null);
    }

    public static void generateMultipleEmployeeReport(List<Integer> userIds, String customFileName, File selectedDirectory, Double minPerformance, Double maxPerformance) throws SQLException, IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String fileName = (customFileName != null && !customFileName.isEmpty()) 
                ? customFileName + ".pdf" 
                : "Raport_Wydajności_" + timestamp + ".pdf";

        File file = (selectedDirectory != null) 
                ? new File(selectedDirectory, fileName) 
                : new File(System.getProperty("user.home"), "Documents/" + fileName);

        InputStream fontStream = EmployeePerformanceReportGenerator.class.getResourceAsStream("/fonts/DejaVuSans.ttf");
        FontProgram fontProgram = FontProgramFactory.createFont(fontStream.readAllBytes());
        PdfFont font = PdfFontFactory.createFont(fontProgram, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);

        StringBuilder queryBuilder = new StringBuilder("""
        SELECT
            e.user_id,
            e.employee,
            u.team_leader_name AS team_leader,
            e.total_tasks,
            e.completed,
            e.canceled,
            e.completed_tasks_titles,
            e.pending_tasks_titles,
            e.completion_rate
        FROM vw_EmployeePerformance e
        LEFT JOIN vw_UserCompleteDetails u ON e.user_id = u.user_id
        WHERE e.user_id = ?
        """);

        // Add performance range filters if provided
        if (minPerformance != null || maxPerformance != null) {
            if (minPerformance != null) {
                queryBuilder.append(" AND e.completion_rate >= ?");
            }
            if (maxPerformance != null) {
                queryBuilder.append(" AND e.completion_rate <= ?");
            }
        }

        String query = queryBuilder.toString();

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             PdfWriter writer = new PdfWriter(file);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            document.setFont(font);

            // Add title and timestamp
            document.add(new Paragraph(userIds.size() > 1 ? "RAPORT WYDAJNOŚCI PRACOWNIKÓW" : "RAPORT WYDAJNOŚCI PRACOWNIKA")
                    .setFontSize(20)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10));

            document.add(new Paragraph("Wygenerowano: " + timestamp)
                    .setFontSize(10)
                    .setItalic()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20));

            boolean hasData = false;

            // Process each employee
            for (int i = 0; i < userIds.size(); i++) {
                int userId = userIds.get(i);
                int paramIndex = 1;

                // Set user ID parameter
                stmt.setInt(paramIndex++, userId);

                // Set performance range parameters if provided
                if (minPerformance != null) {
                    stmt.setDouble(paramIndex++, minPerformance);
                }
                if (maxPerformance != null) {
                    stmt.setDouble(paramIndex++, maxPerformance);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        hasData = true;

                        // Add employee header if multiple employees
                        if (userIds.size() > 1) {
                            document.add(new Paragraph("Pracownik: " + rs.getString("employee"))
                                    .setFontSize(16)
                                    .setBold()
                                    .setMarginTop(i > 0 ? 30 : 0)
                                    .setMarginBottom(10));
                        }

                        // Create employee info table
                        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                                .useAllAvailableWidth()
                                .setMarginBottom(20);

                        String[][] rows = {
                                {"Pracownik", rs.getString("employee")},
                                {"Lider zespołu", rs.getString("team_leader") != null ? rs.getString("team_leader") : "Brak"},
                                {"Liczba zadań", String.valueOf(rs.getInt("total_tasks"))},
                                {"Ukończone", String.valueOf(rs.getInt("completed"))},
                                {"Anulowane", String.valueOf(rs.getInt("canceled"))},
                                {"Współczynnik ukończenia", String.format("%.2f%%", rs.getDouble("completion_rate"))}
                        };

                        for (int j = 0; j < rows.length; j++) {
                            Cell key = new Cell().add(new Paragraph(rows[j][0]).setFont(font)).setBold();
                            Cell value = new Cell().add(new Paragraph(rows[j][1]).setFont(font));
                            if (j % 2 == 0) {
                                key.setBackgroundColor(ColorConstants.LIGHT_GRAY);
                                value.setBackgroundColor(ColorConstants.LIGHT_GRAY);
                            }
                            infoTable.addCell(key);
                            infoTable.addCell(value);
                        }

                        document.add(infoTable);

                        // Add completed tasks section
                        document.add(new Paragraph("Zadania ukończone:")
                                .setFontSize(12).setBold().setMarginBottom(4));

                        String completedTasks = rs.getString("completed_tasks_titles");
                        document.add(new Paragraph(completedTasks != null && !completedTasks.isBlank() ? completedTasks : "Brak")
                                .setMarginBottom(15).setFont(font));

                        // Add pending tasks section
                        document.add(new Paragraph("Zadania oczekujące:")
                                .setFontSize(12).setBold().setMarginBottom(4));

                        String pendingTasks = rs.getString("pending_tasks_titles");
                        document.add(new Paragraph(pendingTasks != null && !pendingTasks.isBlank() ? pendingTasks : "Brak")
                                .setFont(font));
                    }
                }
            }

            if (!hasData) {
                document.add(new Paragraph("Brak danych dla wybranych użytkowników.").setFont(font));
            }
        }

        System.out.println("Raport zapisany jako: " + file.getAbsolutePath());
    }
}
