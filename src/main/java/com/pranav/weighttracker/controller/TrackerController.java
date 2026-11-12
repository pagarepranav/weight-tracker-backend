package com.pranav.weighttracker.controller;

import com.pranav.weighttracker.service.TrackerService;
import com.pranav.weighttracker.service.LogExportService;
import com.pranav.weighttracker.model.MonthlySettings;
import com.pranav.weighttracker.dto.SettingsInput;
import com.pranav.weighttracker.dto.LogInput;
import com.pranav.weighttracker.dto.LogView;
import com.pranav.weighttracker.dto.WeeklySummary;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class TrackerController {
    private final TrackerService service;
    private final LogExportService exportService;
    public TrackerController(TrackerService service, LogExportService exportService) {
        this.service = service;
        this.exportService = exportService;
    }

    @GetMapping("/monthly-settings/{month}")
    MonthlySettings settings(@PathVariable String month) { return service.settings(YearMonth.parse(month)); }

    @PutMapping("/monthly-settings/{month}")
    MonthlySettings saveSettings(@PathVariable String month, @RequestBody SettingsInput input) {
        return service.saveSettings(YearMonth.parse(month), input);
    }

    @PostMapping("/daily-logs/{date}")
    ResponseEntity<LogView> create(@PathVariable LocalDate date, @RequestBody LogInput input) {
        return ResponseEntity.created(URI.create("/api/v1/daily-logs/" + date)).body(service.create(date, input));
    }

    @GetMapping("/daily-logs/{date}")
    LogView get(@PathVariable LocalDate date) { return service.get(date); }

    @PutMapping("/daily-logs/{date}")
    LogView replace(@PathVariable LocalDate date, @RequestBody LogInput input) { return service.replace(date, input); }

    @DeleteMapping("/daily-logs/{date}")
    ResponseEntity<Void> delete(@PathVariable LocalDate date) { service.delete(date); return ResponseEntity.noContent().build(); }

    @GetMapping("/daily-logs")
    List<LogView> list(@RequestParam LocalDate from, @RequestParam LocalDate to) { return service.list(from, to); }

    @GetMapping("/daily-logs/export")
    ResponseEntity<byte[]> export(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        byte[] csv = exportService.export(from, to);
        String filename = "daily-logs-" + from + "-to-" + to + ".csv";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
            .body(csv);
    }

    @GetMapping("/summaries/weekly")
    List<WeeklySummary> weekly(@RequestParam LocalDate from, @RequestParam LocalDate to) { return service.weekly(from, to); }
}
