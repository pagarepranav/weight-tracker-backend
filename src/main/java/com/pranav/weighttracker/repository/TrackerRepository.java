package com.pranav.weighttracker.repository;

import com.pranav.weighttracker.model.DailyLog;
import com.pranav.weighttracker.model.EntryType;
import com.pranav.weighttracker.model.MonthlySettings;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TrackerRepository {
    private final JdbcTemplate jdbc;
    public TrackerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<DailyLog> LOG_MAPPER = (rs, row) -> new DailyLog(
        rs.getObject("log_date", LocalDate.class), EntryType.valueOf(rs.getString("entry_type")),
        rs.getBigDecimal("weight_kg"), rs.getBigDecimal("active_calories"),
        rs.getBigDecimal("intake_calories"), rs.getBigDecimal("target_calories"),
        rs.getBigDecimal("sedentary_calories"), rs.getString("notes"));

    public Optional<MonthlySettings> settings(String month) {
        return jdbc.query("SELECT * FROM monthly_settings WHERE month_key = ?", (rs, row) ->
            new MonthlySettings(rs.getString("month_key"), rs.getBigDecimal("sedentary_calories"),
                rs.getBigDecimal("target_calories")), month).stream().findFirst();
    }

    public void saveSettings(MonthlySettings s) {
        jdbc.update("MERGE INTO monthly_settings (month_key, sedentary_calories, target_calories) KEY(month_key) VALUES (?, ?, ?)",
            s.month(), s.sedentaryCalories(), s.targetCalories());
    }

    public Optional<DailyLog> log(LocalDate date) {
        return jdbc.query("SELECT * FROM daily_logs WHERE log_date = ?", LOG_MAPPER, date).stream().findFirst();
    }

    public List<DailyLog> logs(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT * FROM daily_logs WHERE log_date >= ? AND log_date <= ? ORDER BY log_date",
            LOG_MAPPER, from, to);
    }

    public void insert(DailyLog l) {
        jdbc.update("INSERT INTO daily_logs (log_date, entry_type, weight_kg, active_calories, intake_calories, target_calories, sedentary_calories, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            l.date(), l.entryType().name(), l.weightKg(), l.activeCalories(), l.intakeCalories(),
            l.targetCalories(), l.sedentaryCalories(), l.notes());
    }

    public void update(DailyLog l) {
        jdbc.update("UPDATE daily_logs SET entry_type=?, weight_kg=?, active_calories=?, intake_calories=?, target_calories=?, sedentary_calories=?, notes=? WHERE log_date=?",
            l.entryType().name(), l.weightKg(), l.activeCalories(), l.intakeCalories(),
            l.targetCalories(), l.sedentaryCalories(), l.notes(), l.date());
    }

    public boolean delete(LocalDate date) {
        return jdbc.update("DELETE FROM daily_logs WHERE log_date = ?", date) > 0;
    }
}
