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
import java.util.Optional;

public class ProjectProgressReportGenerator {

    public static void generateReport(int projectId, String customFileName, File selectedDirectory) throws SQLException, IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String fileName = "Raport_postepu_projektu_" + timestamp + ".pdf";

        String userHome = System.getProperty("user.home");
        File file = new File(userHome, "Documents/" + fileName);

        InputStream fontStream = ProjectProgressReportGenerator.class.getResourceAsStream("/fonts/DejaVuSans.ttf");
        FontProgram fontProgram = FontProgramFactory.createFont(fontStream.readAllBytes());
        PdfFont font = PdfFontFactory.createFont(fontProgram, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);

        String query = """
        SELECT
            project,
            manager,
            status,
            overall_progress,
            total_milestones,
            milestone_names,
            total_tasks,
            task_titles,
            completed_tasks,
            canceled_tasks,
            avg_milestone_progress,
            involved_teams,
            team_leaders
        FROM vw_ProjectProgress
        WHERE project_id = ?
        """;

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, projectId);

            try (ResultSet rs = stmt.executeQuery();
                 PdfWriter writer = new PdfWriter(file);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {

                document.setFont(font);

                if (rs.next()) {
                    document.add(new Paragraph("RAPORT POSTĘPU PROJEKTU")
                            .setFontSize(20).setBold()
                            .setTextAlignment(TextAlignment.CENTER).setMarginBottom(10));

                    document.add(new Paragraph("Wygenerowano: " + timestamp)
                            .setFontSize(10).setItalic()
                            .setTextAlignment(TextAlignment.CENTER).setMarginBottom(20));

                    Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                            .useAllAvailableWidth().setMarginBottom(20);

                    String[][] rows = {
                            {"Projekt", rs.getString("project")},
                            {"Menedżer", rs.getString("manager")},
                            {"Status", rs.getString("status")},
                            {"Progres całkowity", rs.getString("overall_progress") + "%"},
                            {"Liczba kamieni milowych", rs.getString("total_milestones")},
                            {"Średni postęp kamieni", rs.getString("avg_milestone_progress") + "%"},
                            {"Liczba zadań", rs.getString("total_tasks")},
                            {"Ukończone zadania", rs.getString("completed_tasks")},
                            {"Anulowane zadania", rs.getString("canceled_tasks")},
                            {"Zespoły", Optional.ofNullable(rs.getString("involved_teams")).orElse("Brak")},
                            {"Liderzy zespołów", Optional.ofNullable(rs.getString("team_leaders")).orElse("Brak")}
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

                    document.add(new Paragraph("Kamienie milowe:")
                            .setFontSize(12).setBold().setMarginBottom(4));
                    document.add(new Paragraph(Optional.ofNullable(rs.getString("milestone_names")).orElse("Brak"))
                            .setFont(font).setMarginBottom(15));

                    document.add(new Paragraph("Zadania w projekcie:")
                            .setFontSize(12).setBold().setMarginBottom(4));
                    document.add(new Paragraph(Optional.ofNullable(rs.getString("task_titles")).orElse("Brak"))
                            .setFont(font));
                } else {
                    document.add(new Paragraph("Brak danych dla wybranego projektu.").setFont(font));
                }
            }
        }

        System.out.println("Raport zapisany jako: " + file.getAbsolutePath());
    }
}
