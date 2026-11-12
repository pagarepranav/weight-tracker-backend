package com.pranav.weighttracker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:tracker-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class TrackerApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private static final String API = "/api/v1";

    @BeforeEach void clean() {
        jdbc.update("DELETE FROM daily_logs");
        jdbc.update("DELETE FROM monthly_settings");
    }

    private void settings() throws Exception {
        mvc.perform(put(API + "/monthly-settings/2026-11").contentType("application/json")
            .content("{\"sedentaryCalories\":1900,\"targetCalories\":1700}"))
            .andExpect(status().isOk());
    }
    private void add(String date, String body) throws Exception {
        mvc.perform(post(API + "/daily-logs/" + date).contentType("application/json").content(body))
            .andExpect(status().isCreated());
    }

    @Test void computesBudgetAndEnergyDeficitSeparately() throws Exception {
        settings();
        mvc.perform(post(API + "/daily-logs/2026-11-12").contentType("application/json")
            .content("{\"weightKg\":72,\"activeCalories\":500,\"intakeCalories\":2000}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location", API + "/daily-logs/2026-11-12"))
            .andExpect(jsonPath("$.entryType").value("ACTUAL"))
            .andExpect(jsonPath("$.totalAllowedCalories").value(2200))
            .andExpect(jsonPath("$.budgetBalanceCalories").value(200))
            .andExpect(jsonPath("$.estimatedEnergyDeficitCalories").value(400));
    }

    @Test void missingDataStaysNullAndExplicitZeroIsValid() throws Exception {
        settings();
        add("2026-11-12", "{\"weightKg\":72}");
        mvc.perform(get(API + "/daily-logs/2026-11-12"))
            .andExpect(jsonPath("$.budgetBalanceCalories").value(nullValue()));
        mvc.perform(put(API + "/daily-logs/2026-11-12").contentType("application/json")
            .content("{\"activeCalories\":0,\"intakeCalories\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.weightKg").value(nullValue()))
            .andExpect(jsonPath("$.budgetBalanceCalories").value(1700));
    }

    @Test void settingsChangesDoNotRewriteHistoricalLogs() throws Exception {
        settings();
        add("2026-11-12", "{\"weightKg\":72,\"activeCalories\":0,\"intakeCalories\":1800}");
        mvc.perform(put(API + "/monthly-settings/2026-11").contentType("application/json")
            .content("{\"sedentaryCalories\":2000,\"targetCalories\":1600}"))
            .andExpect(status().isOk());
        mvc.perform(put(API + "/daily-logs/2026-11-12").contentType("application/json")
            .content("{\"weightKg\":71.9,\"activeCalories\":0,\"intakeCalories\":1800}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.targetCalories").value(1700));
        mvc.perform(get(API + "/daily-logs/2026-11-12"))
            .andExpect(jsonPath("$.targetCalories").value(1700))
            .andExpect(jsonPath("$.budgetBalanceCalories").value(-100));
    }

    @Test void weightOnlyLogCanLaterCaptureSettingsAndPersistThem() throws Exception {
        add("2026-11-12", "{\"weightKg\":72}");
        mvc.perform(put(API + "/daily-logs/2026-11-12").contentType("application/json")
            .content("{\"weightKg\":72,\"intakeCalories\":2000}"))
            .andExpect(status().isConflict());
        settings();
        mvc.perform(put(API + "/daily-logs/2026-11-12").contentType("application/json")
            .content("{\"weightKg\":72,\"activeCalories\":0,\"intakeCalories\":2000}"))
            .andExpect(status().isOk());
        mvc.perform(get(API + "/daily-logs/2026-11-12"))
            .andExpect(jsonPath("$.targetCalories").value(1700))
            .andExpect(jsonPath("$.budgetBalanceCalories").value(-300));
    }

    @Test void weeklySummaryUsesCalendarWeeksAndExcludesPlannedEntries() throws Exception {
        settings();
        add("2026-11-08", "{\"weightKg\":73}"); // Sunday, separate week
        add("2026-11-09", "{\"weightKg\":72,\"activeCalories\":0,\"intakeCalories\":1800}");
        add("2026-11-10", "{\"weightKg\":70}"); // missing calories must not become zero
        add("2026-11-11", "{\"entryType\":\"PLANNED\",\"weightKg\":60,\"activeCalories\":2000,\"intakeCalories\":0}");
        mvc.perform(get(API + "/summaries/weekly?from=2026-11-08&to=2026-11-15"))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].weekStart").value("2026-11-02"))
            .andExpect(jsonPath("$[0].periodStart").value("2026-11-08"))
            .andExpect(jsonPath("$[1].actualLogDays").value(2))
            .andExpect(jsonPath("$[1].weighInDays").value(2))
            .andExpect(jsonPath("$[1].completeCalorieDays").value(1))
            .andExpect(jsonPath("$[1].averageWeightKg").value(71))
            .andExpect(jsonPath("$[1].averageIntakeCalories").value(1800))
            .andExpect(jsonPath("$[1].totalBudgetBalanceCalories").value(-100));
    }

    @Test void emptyWeeksHaveNoInventedTotals() throws Exception {
        mvc.perform(get(API + "/summaries/weekly?from=2026-11-09&to=2026-11-15"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].weighInDays").value(0))
            .andExpect(jsonPath("$[0].averageWeightKg").value(nullValue()))
            .andExpect(jsonPath("$[0].totalBudgetBalanceCalories").value(nullValue()));
    }

    @Test void duplicateDateIsConflictAndDeleteRemovesIt() throws Exception {
        add("2026-11-12", "{\"weightKg\":72}");
        mvc.perform(post(API + "/daily-logs/2026-11-12").contentType("application/json").content("{\"weightKg\":71}"))
            .andExpect(status().isConflict());
        mvc.perform(delete(API + "/daily-logs/2026-11-12")).andExpect(status().isNoContent());
        mvc.perform(get(API + "/daily-logs/2026-11-12")).andExpect(status().isNotFound());
        mvc.perform(put(API + "/daily-logs/2026-11-12").contentType("application/json").content("{\"weightKg\":71}"))
            .andExpect(status().isNotFound());
    }

    @Test void calorieLogRequiresSettings() throws Exception {
        mvc.perform(post(API + "/daily-logs/2026-11-12").contentType("application/json")
            .content("{\"activeCalories\":0}"))
            .andExpect(status().isConflict());
    }

    @Test void rejectsInvalidInputsAndMalformedRequests() throws Exception {
        for (String body : new String[] {"{}", "{\"weightKg\":0}", "{\"weightKg\":-1}",
                "{\"weightKg\":71.123}", "{\"intakeCalories\":-5}", "{\"entryType\":\"BAD\"}",
                "{\"weightKg\":72,\"surprise\":1}", "null", "{"}) {
            mvc.perform(post(API + "/daily-logs/2026-11-12").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(put(API + "/monthly-settings/2026-11").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get(API + "/monthly-settings/not-a-month")).andExpect(status().isBadRequest());
        mvc.perform(get(API + "/daily-logs/not-a-date")).andExpect(status().isBadRequest());
        mvc.perform(get(API + "/daily-logs?from=2026-11-15&to=2026-11-01")).andExpect(status().isBadRequest());
        mvc.perform(get(API + "/daily-logs?from=2020-01-01&to=2026-11-01")).andExpect(status().isBadRequest());
    }

    @Test void listIsInclusiveSortedAndCanFilterByRange() throws Exception {
        add("2026-11-12", "{\"weightKg\":72}");
        add("2026-11-10", "{\"weightKg\":73}");
        add("2026-11-09", "{\"weightKg\":74}");
        mvc.perform(get(API + "/daily-logs?from=2026-11-10&to=2026-11-12"))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].date").value("2026-11-10"))
            .andExpect(jsonPath("$[1].date").value("2026-11-12"));
    }

    @Test void allowsConfiguredFrontendCorsOrigin() throws Exception {
        mvc.perform(options(API + "/daily-logs/2026-11-12")
            .header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options(API + "/daily-logs/2026-11-12")
            .header("Origin", "https://unconfigured.example")
            .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden());
    }

    @Test void exportsAllFieldsWithCalculationsInDateOrder() throws Exception {
        settings();
        add("2026-11-12", "{\"weightKg\":72,\"activeCalories\":500,\"intakeCalories\":2000,\"notes\":\"Walk, \\\"good\\\"\\nCaf\u00e9\"}");
        add("2026-11-10", "{\"entryType\":\"PLANNED\",\"weightKg\":73}");
        add("2026-11-09", "{\"weightKg\":74}");
        mvc.perform(get(API + "/daily-logs/export?from=2026-11-10&to=2026-11-12")
                .header("Origin", "http://localhost:5173"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/csv;charset=UTF-8"))
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"daily-logs-2026-11-10-to-2026-11-12.csv\""))
            .andExpect(header().string("Access-Control-Expose-Headers", containsString("Content-Disposition")))
            .andExpect(content().bytes(("\uFEFFdate,entryType,weightKg,activeCalories,intakeCalories,targetCalories,"
                + "sedentaryCalories,totalAllowedCalories,budgetBalanceCalories,estimatedEnergyDeficitCalories,notes\r\n"
                + "2026-11-10,PLANNED,73.00,,,1700.00,1900.00,,,,\r\n"
                + "2026-11-12,ACTUAL,72.00,500.00,2000.00,1700.00,1900.00,2200.00,200.00,400.00,\"Walk, \"\"good\"\"\nCaf\u00e9\"\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test void exportPreservesMissingValuesAndProtectsFormulaLikeNotes() throws Exception {
        add("2026-11-12", "{\"notes\":\"  =1+1\"}");
        mvc.perform(get(API + "/daily-logs/export?from=2026-11-12&to=2026-11-12"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("2026-11-12,ACTUAL,,,,,,,,,\"'  =1+1\"\r\n")));
    }

    @Test void emptyExportHasHeadersAndInvalidExportRangesAreRejected() throws Exception {
        mvc.perform(get(API + "/daily-logs/export?from=2026-11-12&to=2026-11-12"))
            .andExpect(status().isOk())
            .andExpect(content().string(endsWith("estimatedEnergyDeficitCalories,notes\r\n")));
        for (String query : new String[] {"from=2026-11-15&to=2026-11-01",
                "from=2020-01-01&to=2026-11-01", "from=bad&to=2026-11-12", "from=2026-11-12"}) {
            mvc.perform(get(API + "/daily-logs/export?" + query)).andExpect(status().isBadRequest());
        }
    }
}
