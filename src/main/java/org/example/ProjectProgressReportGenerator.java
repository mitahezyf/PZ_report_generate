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
import java.util.Optional;

public class ProjectProgressReportGenerator {

    public static void generateReport(int projectId, String customFileName, File selectedDirectory) throws SQLException, IOException {
        generateFilteredReport(projectId, customFileName, selectedDirectory, null, null);
    }

    public static void generateMultipleProjectReport(List<Integer> projectIds, String customFileName, File selectedDirectory) throws SQLException, IOException {
        generateMultipleFilteredReport(projectIds, customFileName, selectedDirectory, null, null);
    }

    public static void generateFilteredReport(int projectId, String customFileName, File selectedDirectory, 
                                             String projectStatus, Integer managerId) throws SQLException, IOException {
        List<Integer> projectIds = List.of(projectId);
        generateMultipleFilteredReport(projectIds, customFileName, selectedDirectory, projectStatus, managerId);
    }

    public static void generateMultipleFilteredReport(List<Integer> projectIds, String customFileName, File selectedDirectory, 
                                                    String projectStatus, Integer managerId) throws SQLException, IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String fileName = (customFileName != null && !customFileName.isEmpty()) 
                ? customFileName + ".pdf" 
                : "Raport_postepu_projektu_" + timestamp + ".pdf";

        File file = (selectedDirectory != null) 
                ? new File(selectedDirectory, fileName) 
                : new File(System.getProperty("user.home"), "Documents/" + fileName);

        InputStream fontStream = ProjectProgressReportGenerator.class.getResourceAsStream("/fonts/DejaVuSans.ttf");
        FontProgram fontProgram = FontProgramFactory.createFont(fontStream.readAllBytes());
        PdfFont font = PdfFontFactory.createFont(fontProgram, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);

        StringBuilder queryBuilder = new StringBuilder("""
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
        """);

        // Add filters if provided
        if (projectStatus != null && !projectStatus.isEmpty()) {
            queryBuilder.append(" AND status = ?");
        }

        if (managerId != null) {
            queryBuilder.append(" AND manager_id = ?");
        }

        String query = queryBuilder.toString();

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             PdfWriter writer = new PdfWriter(file);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            document.setFont(font);

            // Add title and timestamp
            document.add(new Paragraph(projectIds.size() > 1 ? "RAPORT POSTĘPU PROJEKTÓW" : "RAPORT POSTĘPU PROJEKTU")
                    .setFontSize(20).setBold()
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(10));

            document.add(new Paragraph("Wygenerowano: " + timestamp)
                    .setFontSize(10).setItalic()
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(20));

            boolean hasData = false;

            // Process each project
            for (int i = 0; i < projectIds.size(); i++) {
                int projectId = projectIds.get(i);
                int paramIndex = 1;

                // Set project ID parameter
                stmt.setInt(paramIndex++, projectId);

                // Set additional filter parameters if provided
                if (projectStatus != null && !projectStatus.isEmpty()) {
                    stmt.setString(paramIndex++, projectStatus);
                }

                if (managerId != null) {
                    stmt.setInt(paramIndex++, managerId);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        hasData = true;

                        // Add a page break before each project (except the first one)
                        if (i > 0) {
                            document.add(new AreaBreak());
                        }

                        // Create a Div to keep all project content together
                        Div projectDiv = new Div();
                        projectDiv.setKeepTogether(true);

                        // Add project header if multiple projects
                        if (projectIds.size() > 1) {
                            projectDiv.add(new Paragraph("Projekt: " + rs.getString("project"))
                                    .setFontSize(16)
                                    .setBold()
                                    .setMarginTop(0)
                                    .setMarginBottom(10));
                        }

                        // Create project info table
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

                        projectDiv.add(infoTable);

                        projectDiv.add(new Paragraph("Kamienie milowe:")
                                .setFontSize(12).setBold().setMarginBottom(4));
                        projectDiv.add(new Paragraph(Optional.ofNullable(rs.getString("milestone_names")).orElse("Brak"))
                                .setFont(font).setMarginBottom(15));

                        projectDiv.add(new Paragraph("Zadania w projekcie:")
                                .setFontSize(12).setBold().setMarginBottom(4));
                        projectDiv.add(new Paragraph(Optional.ofNullable(rs.getString("task_titles")).orElse("Brak"))
                                .setFont(font));

                        // Add the complete project div to the document
                        document.add(projectDiv);
                    }
                }
            }

            if (!hasData) {
                // Create a Div to keep the message together
                Div messageDiv = new Div();
                messageDiv.setKeepTogether(true);
                messageDiv.add(new Paragraph("Brak danych dla wybranych projektów.").setFont(font));
                document.add(messageDiv);
            }
        }

        System.out.println("Raport zapisany jako: " + file.getAbsolutePath());
    }
}
