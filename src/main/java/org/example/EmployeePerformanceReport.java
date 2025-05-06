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

public class EmployeePerformanceReport {

    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;

    public EmployeePerformanceReport(String dbUrl, String dbUsername, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUsername = dbUsername;
        this.dbPassword = dbPassword;
    }

    public void generateReport(String outputFilePath, String employeeName) {
        String sql = "SELECT employee, total_tasks, completed, canceled, completion_rate FROM vw_EmployeePerformance WHERE employee LIKE ?";

        try (Connection connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, "%" + employeeName + "%");
            ResultSet resultSet = statement.executeQuery();

            PdfWriter writer = new PdfWriter(outputFilePath);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Dodanie tytułu raportu
            document.add(new Paragraph("Raport Wydajności Pracownika")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(18)
                    .setBold());

            document.add(new Paragraph("Raport generowany w dniu: " + getCurrentDate())
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(12));

            document.add(new Paragraph(" ")); // Pusta linia

            // Tworzenie tabeli z 5 kolumnami
            Table table = new Table(5);
            table.setWidth(UnitValue.createPercentValue(100));  // Ustawiamy szerokość tabeli na 100%

            // Nagłówki tabeli z pogrubioną czcionką i tłem
            table.addCell(createHeaderCell("Pracownik"));
            table.addCell(createHeaderCell("Wszystkie zadania"));
            table.addCell(createHeaderCell("Zakończone"));
            table.addCell(createHeaderCell("Anulowane"));
            table.addCell(createHeaderCell("Wskaźnik wydajności (%)"));

            // Wypełnianie danymi z bazy
            while (resultSet.next()) {
                table.addCell(new Cell().add(new Paragraph(resultSet.getString("employee"))));
                table.addCell(new Cell().add(new Paragraph(String.valueOf(resultSet.getInt("total_tasks")))));
                table.addCell(new Cell().add(new Paragraph(String.valueOf(resultSet.getInt("completed")))));
                table.addCell(new Cell().add(new Paragraph(String.valueOf(resultSet.getInt("canceled")))));
                table.addCell(new Cell().add(new Paragraph(String.format("%.2f", resultSet.getDouble("completion_rate")))));
            }

            document.add(table);

            document.close();

            System.out.println("Raport PDF został pomyślnie wygenerowany!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Tworzy komórkę nagłówka tabeli z tłem i pogrubionym tekstem
     *
     * @param text Tekst nagłówka
     * @return Komórka nagłówka
     */
    private Cell createHeaderCell(String text) {
        return new Cell()
                .add(new Paragraph(text))
                .setBackgroundColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(12)
                .setBold();
    }

    /**
     * Pobiera bieżącą datę w formacie dd-MM-yyyy
     *
     * @return Bieżąca data
     */
    private String getCurrentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        return sdf.format(new Date());
    }
}
