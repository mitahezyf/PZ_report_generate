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

public class EmployeePerformanceReportGenerator {

    public static void generateReportFiltered(int userId, String customFileName, File selectedDirectory) throws SQLException, IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String fileName = "Raport_Wydajności_" + timestamp + ".pdf";

        String userHome = System.getProperty("user.home");
        File file = new File(userHome, "Documents/" + fileName);

        InputStream fontStream = EmployeePerformanceReportGenerator.class.getResourceAsStream("/fonts/DejaVuSans.ttf");
        FontProgram fontProgram = FontProgramFactory.createFont(fontStream.readAllBytes());
        PdfFont font = PdfFontFactory.createFont(fontProgram, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);

        String query = """
        SELECT
            employee,
            CONCAT(ANY_VALUE(team_leader)) AS team_leader,
            total_tasks,
            completed,
            canceled,
            completed_tasks_titles,
            pending_tasks_titles,
            completion_rate
        FROM vw_EmployeePerformance
        WHERE user_id = ?
        """;

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery();
                 PdfWriter writer = new PdfWriter(file);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {

                document.setFont(font);

                if (rs.next()) {
                    document.add(new Paragraph("RAPORT WYDAJNOŚCI PRACOWNIKA")
                            .setFontSize(20)
                            .setBold()
                            .setTextAlignment(TextAlignment.CENTER)
                            .setMarginBottom(10));

                    document.add(new Paragraph("Wygenerowano: " + timestamp)
                            .setFontSize(10)
                            .setItalic()
                            .setTextAlignment(TextAlignment.CENTER)
                            .setMarginBottom(20));

                    Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                            .useAllAvailableWidth()
                            .setMarginBottom(20);

                    String[][] rows = {
                            {"Pracownik", rs.getString("employee")},
                            {"Lider zespołu", rs.getString("team_leader")},
                            {"Liczba zadań", String.valueOf(rs.getInt("total_tasks"))},
                            {"Ukończone", String.valueOf(rs.getInt("completed"))},
                            {"Anulowane", String.valueOf(rs.getInt("canceled"))},
                            {"Współczynnik ukończenia", String.format("%.2f%%", rs.getDouble("completion_rate"))}
                    };

                    for (int i = 0; i < rows.length; i++) {
                        Cell key = new Cell().add(new Paragraph(rows[i][0]).setFont(font)).setBold();
                        Cell value = new Cell().add(new Paragraph(rows[i][1]).setFont(font));
                        if (i % 2 == 0) {
                            key.setBackgroundColor(ColorConstants.LIGHT_GRAY);
                            value.setBackgroundColor(ColorConstants.LIGHT_GRAY);
                        }
                        infoTable.addCell(key);
                        infoTable.addCell(value);
                    }

                    document.add(infoTable);

                    document.add(new Paragraph("Zadania ukończone:")
                            .setFontSize(12).setBold().setMarginBottom(4));

                    String completedTasks = rs.getString("completed_tasks_titles");
                    document.add(new Paragraph(completedTasks != null && !completedTasks.isBlank() ? completedTasks : "Brak")
                            .setMarginBottom(15).setFont(font));

                    document.add(new Paragraph("Zadania oczekujące:")
                            .setFontSize(12).setBold().setMarginBottom(4));

                    String pendingTasks = rs.getString("pending_tasks_titles");
                    document.add(new Paragraph(pendingTasks != null && !pendingTasks.isBlank() ? pendingTasks : "Brak")
                            .setFont(font));
                } else {
                    document.add(new Paragraph("Brak danych dla podanego użytkownika.").setFont(font));
                }
            }
        }

        System.out.println("Raport zapisany jako: " + file.getAbsolutePath());
    }
}