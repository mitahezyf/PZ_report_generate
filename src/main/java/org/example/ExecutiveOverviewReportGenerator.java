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

public class ExecutiveOverviewReportGenerator {

    // Cache for the font to avoid loading it multiple times
    private static PdfFont cachedFont;

    // Static initialization of the font
    static {
        try {
            InputStream fontStream = ExecutiveOverviewReportGenerator.class.getResourceAsStream("/fonts/DejaVuSans.ttf");
            if (fontStream != null) {
                FontProgram fontProgram = FontProgramFactory.createFont(fontStream.readAllBytes());
                cachedFont = PdfFontFactory.createFont(fontProgram, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            }
        } catch (IOException e) {
            System.err.println("Error loading font: " + e.getMessage());
        }
    }

    /**
     * Generates an executive overview report for a project.
     * 
     * @param projectId The ID of the project
     * @param customFileName Custom file name for the report
     * @param selectedDirectory Directory to save the report
     * @throws SQLException If a database error occurs
     * @throws IOException If an I/O error occurs
     */
    public static void generateReport(int projectId, String customFileName, File selectedDirectory) throws SQLException, IOException {
        generateFilteredReport(projectId, customFileName, selectedDirectory, null, null, false, false, null, null);
    }

    /**
     * Generates an executive overview report for a project with filtering options.
     * 
     * @param projectId The ID of the project
     * @param customFileName Custom file name for the report
     * @param selectedDirectory Directory to save the report
     * @param projectStatus Filter by project status
     * @param managerId Filter by manager ID
     * @param showOverdueTasks Show only projects with overdue tasks
     * @param showOverdueMilestones Show only projects with overdue milestones
     * @param minCompletionRate Minimum task completion rate
     * @param maxCompletionRate Maximum task completion rate
     * @throws SQLException If a database error occurs
     * @throws IOException If an I/O error occurs
     */

    /**
     * ProjectData DTO class to store project information
     */
    private static class ProjectData {
        private String project;
        private String projectStatus;
        private String projectProgress;
        private String projectManager;
        private String teamsInvolved;
        private String employeesAssigned;
        private String milestones;
        private String totalTasks;
        private String tasksDone;
        private String tasksCanceled;
        private String taskCompletionRate;
        private String avgMilestoneProgress;
        private String overdueMilestones;
        private String overdueTasks;
        private String involvedTeams;
        private String teamLeaders;
        private String taskTitles;

        // Check if the project data is empty
        public boolean isEmpty() {
            return project == null || project.isEmpty();
        }

        // Get methods with null handling
        public String getProject() {
            return project != null ? project : "";
        }

        public String getProjectStatus() {
            return projectStatus != null ? projectStatus : "";
        }

        public String getProjectProgress() {
            return projectProgress != null ? projectProgress : "0";
        }

        public String getProjectManager() {
            return projectManager != null ? projectManager : "";
        }

        public String getTeamsInvolved() {
            return teamsInvolved != null ? teamsInvolved : "0";
        }

        public String getEmployeesAssigned() {
            return employeesAssigned != null ? employeesAssigned : "0";
        }

        public String getMilestones() {
            return milestones != null ? milestones : "0";
        }

        public String getTotalTasks() {
            return totalTasks != null ? totalTasks : "0";
        }

        public String getTasksDone() {
            return tasksDone != null ? tasksDone : "0";
        }

        public String getTasksCanceled() {
            return tasksCanceled != null ? tasksCanceled : "0";
        }

        public String getTaskCompletionRate() {
            return taskCompletionRate != null ? taskCompletionRate : "0";
        }

        public String getAvgMilestoneProgress() {
            return avgMilestoneProgress != null ? avgMilestoneProgress : "0";
        }

        public String getOverdueMilestones() {
            return overdueMilestones != null ? overdueMilestones : "0";
        }

        public String getOverdueTasks() {
            return overdueTasks != null ? overdueTasks : "0";
        }

        public String getInvolvedTeams() {
            return involvedTeams != null ? involvedTeams : "Brak";
        }

        public String getTeamLeaders() {
            return teamLeaders != null ? teamLeaders : "Brak";
        }

        public String getTaskTitles() {
            return taskTitles != null ? taskTitles : "Brak";
        }
    }

    public static void generateFilteredReport(int projectId, String customFileName, File selectedDirectory,
                                            String projectStatus, Integer managerId,
                                            boolean showOverdueTasks, boolean showOverdueMilestones,
                                            Double minCompletionRate, Double maxCompletionRate) throws SQLException, IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String fileName = (customFileName != null && !customFileName.isEmpty()) 
                ? customFileName + ".pdf" 
                : "Raport_zarzadczy_" + timestamp + ".pdf";

        File file = (selectedDirectory != null) 
                ? new File(selectedDirectory, fileName) 
                : new File(System.getProperty("user.home"), "Documents/" + fileName);

        // Use cached font or load it if not available
        PdfFont font = cachedFont;
        if (font == null) {
            try (InputStream fontStream = ExecutiveOverviewReportGenerator.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
                if (fontStream != null) {
                    FontProgram fontProgram = FontProgramFactory.createFont(fontStream.readAllBytes());
                    font = PdfFontFactory.createFont(fontProgram, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                    cachedFont = font;
                }
            }
        }

        // Create a single optimized SQL query using the vw_ExecutiveOverview view
        StringBuilder queryBuilder = new StringBuilder(
            "SELECT v.*, p.manager_id FROM vw_ExecutiveOverview v JOIN Projects p ON v.project_id = p.id WHERE v.project_id = ?"
        );

        // Add dynamic WHERE conditions
        if (projectStatus != null && !projectStatus.isEmpty()) {
            queryBuilder.append(" AND v.project_status = ?");
        } else {
            queryBuilder.append(" AND (? IS NULL OR v.project_status = ?)");
        }

        if (managerId != null) {
            queryBuilder.append(" AND p.manager_id = ?");
        } else {
            queryBuilder.append(" AND (? IS NULL OR p.manager_id = ?)");
        }

        if (showOverdueTasks) {
            queryBuilder.append(" AND v.overdue_tasks > 0");
        } else {
            queryBuilder.append(" AND (v.overdue_tasks > 0 OR ? = FALSE)");
        }

        if (showOverdueMilestones) {
            queryBuilder.append(" AND v.overdue_milestones > 0");
        } else {
            queryBuilder.append(" AND (v.overdue_milestones > 0 OR ? = FALSE)");
        }

        if (minCompletionRate != null || maxCompletionRate != null) {
            if (minCompletionRate != null) {
                queryBuilder.append(" AND v.task_completion_rate >= ?");
            }
            if (maxCompletionRate != null) {
                queryBuilder.append(" AND v.task_completion_rate <= ?");
            }
        } else {
            queryBuilder.append(" AND v.task_completion_rate BETWEEN ? AND ?");
        }

        // Execute the query and get the project data
        ProjectData projectData = new ProjectData();

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(queryBuilder.toString())) {

            int paramIndex = 1;

            // Set project ID
            stmt.setInt(paramIndex++, projectId);

            // Set project status
            if (projectStatus != null && !projectStatus.isEmpty()) {
                stmt.setString(paramIndex++, projectStatus);
            } else {
                stmt.setNull(paramIndex++, Types.VARCHAR);
                stmt.setString(paramIndex++, "");  // Dummy value, won't be used
            }

            // Set manager ID
            if (managerId != null) {
                stmt.setInt(paramIndex++, managerId);
            } else {
                stmt.setNull(paramIndex++, Types.INTEGER);
                stmt.setInt(paramIndex++, 0);  // Dummy value, won't be used
            }

            // Set overdue tasks flag
            if (!showOverdueTasks) {
                stmt.setBoolean(paramIndex++, showOverdueTasks);
            }

            // Set overdue milestones flag
            if (!showOverdueMilestones) {
                stmt.setBoolean(paramIndex++, showOverdueMilestones);
            }

            // Set completion rate range
            if (minCompletionRate != null || maxCompletionRate != null) {
                if (minCompletionRate != null) {
                    stmt.setDouble(paramIndex++, minCompletionRate);
                }
                if (maxCompletionRate != null) {
                    stmt.setDouble(paramIndex++, maxCompletionRate);
                }
            } else {
                stmt.setDouble(paramIndex++, 0);
                stmt.setDouble(paramIndex++, 100);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // Map ResultSet to ProjectData DTO
                    projectData.project = rs.getString("project");
                    projectData.projectStatus = rs.getString("project_status");
                    projectData.projectProgress = rs.getString("project_progress");
                    projectData.projectManager = rs.getString("project_manager");
                    projectData.teamsInvolved = rs.getString("teams_involved");
                    projectData.employeesAssigned = rs.getString("employees_assigned");
                    projectData.milestones = rs.getString("milestones");
                    projectData.totalTasks = rs.getString("total_tasks");
                    projectData.tasksDone = rs.getString("tasks_done");
                    projectData.tasksCanceled = rs.getString("tasks_canceled");

                    // Handle NULL values for task_completion_rate and avg_milestone_progress
                    String taskCompletionRate = rs.getString("task_completion_rate");
                    projectData.taskCompletionRate = (taskCompletionRate != null) ? taskCompletionRate : "0";

                    String avgMilestoneProgress = rs.getString("avg_milestone_progress");
                    projectData.avgMilestoneProgress = (avgMilestoneProgress != null) ? avgMilestoneProgress : "0";

                    projectData.overdueMilestones = rs.getString("overdue_milestones");
                    projectData.overdueTasks = rs.getString("overdue_tasks");
                    projectData.involvedTeams = rs.getString("involved_teams");
                    projectData.teamLeaders = rs.getString("team_leaders");
                    projectData.taskTitles = rs.getString("task_titles");
                }
            }
        }

        // Check if project data is empty
        if (projectData.isEmpty()) {
            try (PdfWriter writer = new PdfWriter(file);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {

                if (font != null) {
                    document.setFont(font);
                }

                // Create a Div to keep the message together
                Div messageDiv = new Div();
                messageDiv.setKeepTogether(true);
                messageDiv.add(new Paragraph("Brak danych dla wybranego projektu."));
                document.add(messageDiv);
            }
            System.out.println("Raport zapisany jako: " + file.getAbsolutePath());
            return;
        }

        // Generate the PDF report with the collected data
        try (PdfWriter writer = new PdfWriter(file);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            if (font != null) {
                document.setFont(font);
            }

            // Add title and timestamp outside the keepTogether div
            document.add(new Paragraph("RAPORT ZARZĄDCZY PROJEKTU")
                    .setFontSize(20).setBold()
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(10));

            document.add(new Paragraph("Wygenerowano: " + timestamp)
                    .setFontSize(10).setItalic()
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(20));

            // Create a Div to keep all report content together
            Div reportDiv = new Div();
            reportDiv.setKeepTogether(true);

            Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                    .useAllAvailableWidth().setMarginBottom(20);

            // Define table rows with data from our ProjectData object
            String[][] rows = {
                    {"Projekt", projectData.getProject()},
                    {"Status", projectData.getProjectStatus()},
                    {"Postęp projektu", projectData.getProjectProgress() + "%"},
                    {"Menedżer projektu", projectData.getProjectManager()},
                    {"Liczba zespołów", projectData.getTeamsInvolved()},
                    {"Liczba pracowników", projectData.getEmployeesAssigned()},
                    {"Liczba kamieni milowych", projectData.getMilestones()},
                    {"Liczba zadań", projectData.getTotalTasks()},
                    {"Zadania zakończone", projectData.getTasksDone()},
                    {"Zadania anulowane", projectData.getTasksCanceled()},
                    {"% ukończonych zadań", projectData.getTaskCompletionRate() + "%"},
                    {"Średni postęp kamieni", projectData.getAvgMilestoneProgress() + "%"},
                    {"Opóźnione kamienie milowe", projectData.getOverdueMilestones()},
                    {"Opóźnione zadania", projectData.getOverdueTasks()},
                    {"Zespoły", projectData.getInvolvedTeams()},
                    {"Liderzy zespołów", projectData.getTeamLeaders()}
            };

            for (int i = 0; i < rows.length; i++) {
                Cell key = new Cell().add(new Paragraph(rows[i][0])).setBold();
                Cell value = new Cell().add(new Paragraph(rows[i][1]));
                if (i % 2 == 0) {
                    key.setBackgroundColor(ColorConstants.LIGHT_GRAY);
                    value.setBackgroundColor(ColorConstants.LIGHT_GRAY);
                }
                infoTable.addCell(key);
                infoTable.addCell(value);
            }

            reportDiv.add(infoTable);

            reportDiv.add(new Paragraph("Zadania w projekcie:")
                    .setFontSize(12).setBold().setMarginBottom(4));
            reportDiv.add(new Paragraph(projectData.getTaskTitles()));

            // Add the complete report div to the document
            document.add(reportDiv);
        }

        System.out.println("Raport zapisany jako: " + file.getAbsolutePath());
    }
}
