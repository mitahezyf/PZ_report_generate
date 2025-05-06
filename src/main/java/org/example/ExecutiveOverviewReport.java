package org.example;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.kernel.colors.ColorConstants;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ExecutiveOverviewReport {

    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;

    public ExecutiveOverviewReport(String dbUrl, String dbUsername, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUsername = dbUsername;
        this.dbPassword = dbPassword;
    }

    public void generateReport(String outputFilePath, String projectName) {
        String sql = "SELECT project, project_status, project_progress, teams_involved, employees_assigned, milestones, tasks_done, tasks_canceled " +
                "FROM vw_ExecutiveOverview WHERE project LIKE ?";

        try (Connection connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, "%" + projectName + "%");
            ResultSet resultSet = statement.executeQuery();

            PdfWriter writer = new PdfWriter(outputFilePath);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Dodanie tytułu raportu
            document.add(new Paragraph("Raport Podsumowania Wykonania Projektu")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(18)
                    .setBold());

            document.add(new Paragraph("Raport generowany w dniu: " + getCurrentDate())
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(12));

            document.add(new Paragraph(" ")); // Pusta linia

            // Tworzenie nagłówka z ogólnymi informacjami
            document.add(new Paragraph("Ogólny Status Projektu")
                    .setTextAlignment(TextAlignment.LEFT)
                    .setFontSize(14)
                    .setBold());

            // Tworzymy tabelę z 8 kolumnami
            Table table = new Table(8);
            table.setWidth(UnitValue.createPercentValue(100)); // Ustawiamy szerokość tabeli na 100%

            // Nagłówki tabeli
            table.addCell(createHeaderCell("Projekt"));
            table.addCell(createHeaderCell("Status Projektu"));
            table.addCell(createHeaderCell("Postęp Projektu (%)"));
            table.addCell(createHeaderCell("Zespoły Zaangażowane"));
            table.addCell(createHeaderCell("Pracownicy Zaangażowani"));
            table.addCell(createHeaderCell("Kamienie Milowe"));
            table.addCell(createHeaderCell("Zadania Wykonane"));
            table.addCell(createHeaderCell("Zadania Anulowane"));

            // Wypełnianie tabeli z danymi
            while (resultSet.next()) {
                table.addCell(new Cell().add(new Paragraph(resultSet.getString("project"))));
                table.addCell(new Cell().add(new Paragraph(resultSet.getString("project_status"))));
                table.addCell(new Cell().add(new Paragraph(String.format("%.2f", resultSet.getDouble("project_progress")))));
                table.addCell(new Cell().add(new Paragraph(String.valueOf(resultSet.getInt("teams_involved")))));
                table.addCell(new Cell().add(new Paragraph(String.valueOf(resultSet.getInt("employees_assigned")))));
                table.addCell(new Cell().add(new Paragraph(String.valueOf(resultSet.getInt("milestones")))));
                table.addCell(new Cell().add(new Paragraph(String.valueOf(resultSet.getInt("tasks_done")))));
                table.addCell(new Cell().add(new Paragraph(String.valueOf(resultSet.getInt("tasks_canceled")))));
            }

            // Dodanie tabeli do dokumentu
            document.add(table);
            document.close();

            System.out.println("Raport Podsumowania Wykonania Projektu PDF został pomyślnie wygenerowany!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Cell createHeaderCell(String text) {
        return new Cell()
                .add(new Paragraph(text))
                .setBackgroundColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(12)
                .setBold();
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("dd-MM-yyyy").format(new Date());
    }
}
