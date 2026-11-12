package com.pranav.weighttracker.service;

import com.pranav.weighttracker.dto.LogView;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class LogExportService {
    private final TrackerService tracker;

    public LogExportService(TrackerService tracker) { this.tracker = tracker; }

    public byte[] export(LocalDate from, LocalDate to) {
        // UTF-8 BOM allows Excel to recognize Unicode notes when opening the file.
        var csv = new StringBuilder("\uFEFFdate,entryType,weightKg,activeCalories,intakeCalories,"
            + "targetCalories,sedentaryCalories,totalAllowedCalories,budgetBalanceCalories,"
            + "estimatedEnergyDeficitCalories,notes\r\n");
        for (LogView log : tracker.list(from, to)) {
            Object[] values = {log.date(), log.entryType(), log.weightKg(), log.activeCalories(),
                log.intakeCalories(), log.targetCalories(), log.sedentaryCalories(),
                log.totalAllowedCalories(), log.budgetBalanceCalories(), log.estimatedEnergyDeficitCalories()};
            for (Object value : values) {
                if (value != null) csv.append(value);
                csv.append(',');
            }
            csv.append(notesCell(log.notes())).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String notesCell(String notes) {
        if (notes == null) return "";
        // Keep user text from being evaluated as a spreadsheet formula.
        String trimmed = notes.stripLeading();
        if ((!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0)
                || (!notes.isEmpty() && "\t\r\n".indexOf(notes.charAt(0)) >= 0)) {
            notes = "'" + notes;
        }
        return "\"" + notes.replace("\"", "\"\"") + "\"";
    }
}
