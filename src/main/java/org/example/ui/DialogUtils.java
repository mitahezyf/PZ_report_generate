package org.example.ui;

import javafx.scene.control.ChoiceDialog;
import java.util.Map;

/**
 * Utility class for creating and showing dialogs.
 */
public class DialogUtils {

    /**
     * Shows a simple selection dialog with a dropdown list.
     * 
     * @param title The dialog title
     * @param label The label for the dropdown
     * @param options The options to display in the dropdown
     * @param onSelected Callback when an option is selected
     */
    public static void showSelectionDialog(String title, String label, Map<String, Integer> options,
                                          BiConsumer<String, Integer> onSelected) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(options.keySet().iterator().next(), options.keySet());
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.setContentText(label);
        dialog.showAndWait().ifPresent(name -> onSelected.accept(name, options.get(name)));
    }

    /**
     * Functional interface for handling selection of a key-value pair.
     */
    @FunctionalInterface
    public interface BiConsumer<K, V> {
        void accept(K k, V v);
    }

    /**
     * Functional interface for handling selection of a map.
     */
    @FunctionalInterface
    public interface MapConsumer<T> {
        void accept(T t);
    }

    /**
     * Functional interface for handling selection of a map with performance range.
     */
    @FunctionalInterface
    public interface PerformanceMapConsumer<T> {
        void accept(T t, Double minPerformance, Double maxPerformance);
    }

    /**
     * Functional interface for handling selection of a project with filters.
     */
    @FunctionalInterface
    public interface ProjectFilterConsumer<K, V> {
        void accept(K name, V id, String status, Integer managerId);
    }

    /**
     * Functional interface for handling selection of multiple projects with filters.
     */
    @FunctionalInterface
    public interface ProjectMultiFilterConsumer<T> {
        void accept(T selectedProjects, String status, Integer managerId);
    }

    /**
     * Functional interface for handling selection of a project with executive report filters.
     */
    @FunctionalInterface
    public interface ExecutiveReportFilterConsumer<K, V> {
        void accept(K name, V id, String status, Integer managerId, 
                   Boolean showOverdueTasks, Boolean showOverdueMilestones,
                   Double minCompletionRate, Double maxCompletionRate);
    }
}