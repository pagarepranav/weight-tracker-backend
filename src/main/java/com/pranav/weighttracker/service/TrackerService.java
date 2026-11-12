package com.pranav.weighttracker.service;

import com.pranav.weighttracker.repository.TrackerRepository;
import com.pranav.weighttracker.model.EntryType;
import com.pranav.weighttracker.model.DailyLog;
import com.pranav.weighttracker.model.MonthlySettings;
import com.pranav.weighttracker.dto.SettingsInput;
import com.pranav.weighttracker.dto.LogInput;
import com.pranav.weighttracker.dto.LogView;
import com.pranav.weighttracker.dto.WeeklySummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrackerService {
    private final TrackerRepository repository;
    public TrackerService(TrackerRepository repository) { this.repository = repository; }

    public MonthlySettings settings(YearMonth month) {
        return repository.settings(month.toString()).orElseThrow(() -> missing("Monthly settings not found"));
    }

    @Transactional
    public MonthlySettings saveSettings(YearMonth month, SettingsInput input) {
        number("sedentaryCalories", input.sedentaryCalories(), true, true, new BigDecimal("100000"));
        number("targetCalories", input.targetCalories(), true, true, new BigDecimal("100000"));
        var result = new MonthlySettings(month.toString(), input.sedentaryCalories(), input.targetCalories());
        repository.saveSettings(result);
        return result;
    }

    public LogView get(LocalDate date) {
        return view(repository.log(date).orElseThrow(() -> missing("Daily log not found")));
    }

    public List<LogView> list(LocalDate from, LocalDate to) {
        range(from, to);
        return repository.logs(from, to).stream().map(TrackerService::view).toList();
    }

    @Transactional
    public LogView create(LocalDate date, LogInput input) {
        validate(input);
        // Snapshot settings so later edits to monthly settings do not rewrite history.
        var settings = repository.settings(YearMonth.from(date).toString()).orElse(null);
        if ((input.intakeCalories() != null || input.activeCalories() != null) && settings == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Create monthly settings before recording calorie inputs");
        }
        var log = new DailyLog(date, type(input), input.weightKg(), input.activeCalories(),
            input.intakeCalories(), settings == null ? null : settings.targetCalories(),
            settings == null ? null : settings.sedentaryCalories(), input.notes());
        repository.insert(log);
        return view(log);
    }

    @Transactional
    public LogView replace(LocalDate date, LogInput input) {
        validate(input);
        var old = repository.log(date).orElseThrow(() -> missing("Daily log not found"));
        // A weight-only log may not have had settings. Capture them when calories are first added.
        var target = old.targetCalories();
        var sedentary = old.sedentaryCalories();
        if (target == null && (input.intakeCalories() != null || input.activeCalories() != null)) {
            var settings = repository.settings(YearMonth.from(date).toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Create monthly settings before recording calorie inputs"));
            target = settings.targetCalories();
            sedentary = settings.sedentaryCalories();
        }
        var log = new DailyLog(date, type(input), input.weightKg(), input.activeCalories(),
            input.intakeCalories(), target, sedentary, input.notes());
        repository.update(log);
        return view(log);
    }

    public void delete(LocalDate date) {
        if (!repository.delete(date)) throw missing("Daily log not found");
    }

    public List<WeeklySummary> weekly(LocalDate from, LocalDate to) {
        range(from, to);
        var logs = repository.logs(from, to).stream().filter(l -> l.entryType() == EntryType.ACTUAL).toList();
        var result = new ArrayList<WeeklySummary>();
        for (var monday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
             !monday.isAfter(to); monday = monday.plusWeeks(1)) {
            var start = monday.isBefore(from) ? from : monday;
            var sunday = monday.plusDays(6);
            var end = sunday.isAfter(to) ? to : sunday;
            var week = logs.stream().filter(l -> !l.date().isBefore(start) && !l.date().isAfter(end)).toList();
            var views = week.stream().map(TrackerService::view).toList();
            result.add(new WeeklySummary(monday, sunday, start, end, week.size(),
                (int) week.stream().filter(l -> l.weightKg() != null).count(),
                (int) views.stream().filter(l -> l.budgetBalanceCalories() != null).count(),
                average(week, DailyLog::weightKg), average(week, DailyLog::intakeCalories),
                average(week, DailyLog::activeCalories), sum(views, LogView::budgetBalanceCalories),
                sum(views, LogView::estimatedEnergyDeficitCalories)));
        }
        return result;
    }

    static LogView view(DailyLog log) {
        var allowed = add(log.targetCalories(), log.activeCalories());
        var balance = subtract(allowed, log.intakeCalories());
        var deficit = subtract(add(log.sedentaryCalories(), log.activeCalories()), log.intakeCalories());
        return new LogView(log.date(), log.entryType(), log.weightKg(), log.activeCalories(),
            log.intakeCalories(), log.targetCalories(), log.sedentaryCalories(), allowed, balance, deficit, log.notes());
    }

    private static BigDecimal add(BigDecimal a, BigDecimal b) { return a == null || b == null ? null : a.add(b); }
    private static BigDecimal subtract(BigDecimal a, BigDecimal b) { return a == null || b == null ? null : a.subtract(b); }
    private static <T> BigDecimal sum(List<T> items, Function<T, BigDecimal> field) {
        return items.stream().map(field).filter(x -> x != null).reduce(BigDecimal::add).orElse(null);
    }
    private static <T> BigDecimal average(List<T> items, Function<T, BigDecimal> field) {
        long count = items.stream().map(field).filter(x -> x != null).count();
        return count == 0 ? null : sum(items, field).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
    private static EntryType type(LogInput input) { return input.entryType() == null ? EntryType.ACTUAL : input.entryType(); }
    private static void validate(LogInput input) {
        number("weightKg", input.weightKg(), false, true, new BigDecimal("1000"));
        number("activeCalories", input.activeCalories(), false, false, new BigDecimal("100000"));
        number("intakeCalories", input.intakeCalories(), false, false, new BigDecimal("100000"));
        if (input.notes() != null && input.notes().length() > 2000) throw new IllegalArgumentException("notes must be at most 2000 characters");
        if (input.weightKg() == null && input.activeCalories() == null && input.intakeCalories() == null
            && (input.notes() == null || input.notes().isBlank())) throw new IllegalArgumentException("Provide a weight, calorie input, or note");
    }
    private static void number(String name, BigDecimal value, boolean required, boolean positive, BigDecimal max) {
        if (value == null) {
            if (required) throw new IllegalArgumentException(name + " is required");
            return;
        }
        if ((positive ? value.signum() <= 0 : value.signum() < 0) || value.compareTo(max) > 0
            || value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(name + " must be " + (positive ? "greater than zero" : "zero or greater")
                + ", at most " + max + ", and have at most two decimal places");
        }
    }
    private static void range(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new IllegalArgumentException("from must be on or before to");
        if (to.isAfter(from.plusYears(5))) throw new IllegalArgumentException("Date range must be at most five years");
    }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
