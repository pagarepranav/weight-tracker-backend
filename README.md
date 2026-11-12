# Weight Tracker Backend

A personal Java backend for the weight and calorie tracking workflow in your spreadsheet. The frontend can send JSON over HTTP when it is built.

## Stack and startup

- Java 17, Spring Boot 4.1.1, Maven
- Spring MVC REST API and Spring JDBC
- File-based H2 database, stored in `data/weight-tracker.mv.db`

Run from this project directory:

```powershell
mvn test
mvn spring-boot:run
```

The API listens at `http://127.0.0.1:8080/api/v1`. Stop it with Ctrl+C. The database survives restarts. There is no sample data or spreadsheet import, so your database starts empty.

To build and run a standalone JAR:

```powershell
mvn package
java -jar target/weight-tracker-0.1.0-SNAPSHOT.jar
```

## API

Dates use `YYYY-MM-DD`; months use `YYYY-MM`. Request and response bodies use JSON. All weights are kilograms and all calorie values are kcal. Numeric inputs accept up to two decimal places. Weight must be above zero and at most 1,000; calories must be nonnegative and at most 100,000, with monthly settings strictly above zero. These bounds protect input/storage and are not dietary recommendations.

| Method | Path relative to `/api/v1` | Behavior |
| --- | --- | --- |
| PUT | `/monthly-settings/{month}` | Create or replace monthly calorie settings |
| GET | `/monthly-settings/{month}` | Read monthly settings |
| POST | `/daily-logs/{date}` | Create a daily log; 201 with Location header |
| PUT | `/daily-logs/{date}` | Replace an existing log's inputs; 404 if missing |
| GET | `/daily-logs/{date}` | Read one log and its calculations |
| DELETE | `/daily-logs/{date}` | Delete a log; 204, or 404 if missing |
| GET | `/daily-logs?from=2026-11-01&to=2026-11-30` | List logs in an inclusive range, ordered by date |
| GET | `/daily-logs/export?from=2026-11-01&to=2026-11-30` | Download logs and calculated values as an Excel-compatible CSV |
| GET | `/summaries/weekly?from=2026-11-01&to=2026-11-30` | Summarize actual entries by Mondayâ€“Sunday weeks |

Ranges require both dates, must be chronological, and may span at most five years. Invalid input returns 400, a missing record returns 404, and duplicate dates or calorie inputs without monthly settings return 409. Error responses use Spring's problem-details JSON.

### 1. Set monthly settings

```powershell
Invoke-RestMethod -Method Put -Uri 'http://127.0.0.1:8080/api/v1/monthly-settings/2026-11' -ContentType 'application/json' -Body '{"sedentaryCalories":1900,"targetCalories":1700}'
```

### 2. Record a day

```powershell
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/v1/daily-logs/2026-11-12' -ContentType 'application/json' -Body '{"entryType":"ACTUAL","weightKg":72,"activeCalories":500,"intakeCalories":2000,"notes":"Evening walk"}'
```

Response:

```json
{
  "date": "2026-11-12",
  "entryType": "ACTUAL",
  "weightKg": 72,
  "activeCalories": 500,
  "intakeCalories": 2000,
  "targetCalories": 1700,
  "sedentaryCalories": 1900,
  "totalAllowedCalories": 2200,
  "budgetBalanceCalories": 200,
  "estimatedEnergyDeficitCalories": 400,
  "notes": "Evening walk"
}
```

### 3. Read summaries

```powershell
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/summaries/weekly?from=2026-11-01&to=2026-11-30'
```

### 4. Download daily logs

```powershell
curl.exe --output daily-logs.csv "http://localhost:8080/api/v1/daily-logs/export?from=2026-11-01&to=2026-11-30"
```

Open the downloaded CSV in Excel or import it into Google Sheets. In Postman, use **Send and Download** for this GET request. The export includes every `LogView` field, both actual and planned entries, in date order. Dates are inclusive; use the same `from` and `to` to export one day. Missing values are blank and zero values remain zero. An empty range returns column headers. Unicode notes, quotes, commas, and line breaks are supported; formula-like notes are prefixed with an apostrophe for safe spreadsheet display. The download uses `text/csv;charset=UTF-8` and a `Content-Disposition` attachment filename.

## Calculation and history rules

- `totalAllowedCalories = targetCalories + activeCalories`
- `budgetBalanceCalories = totalAllowedCalories - intakeCalories`
- `estimatedEnergyDeficitCalories = sedentaryCalories + activeCalories - intakeCalories`
- A positive balance means below budget. A positive estimated deficit means estimated expenditure exceeds intake.
- The estimated expenditure calculation assumes the supplied activity calories are additional to the sedentary estimate. It is an estimate, not a measurement of energy expenditure.
- Missing inputs remain `null`. Enter `0` explicitly for a known zero. A missing activity value does not mean no activity.
- Weight-only and notes-only logs are allowed without monthly settings. Calorie inputs require settings for that log's month.
- Settings are captured when a log is created if available, or when calorie inputs are first added to a weight-only log. Subsequent monthly settings edits affect new logs, preserving existing snapshots. PUT replaces the log's input fields but preserves those snapshots.
- `entryType` defaults to `ACTUAL`. Use `PLANNED` for forecasts, including future calorie plans. Future dates are permitted; callers must explicitly mark planned data. There is one log per date, so PUT can convert a planned log to an actual log.
- Weekly summaries exclude `PLANNED` logs, average each field over its recorded values, and sum balances only for days with all calorie inputs. `completeCalorieDays` tells you how much data supports those totals. A no-data total is `null`, not zero.
- `weekStart`/`weekEnd` identify Mondayâ€“Sunday. `periodStart`/`periodEnd` identify the portion within the requested range. Empty weeks are included with zero counts and null metrics.
- PUT is a complete replacement, not a partial update. Omitted optional inputs become null. You must send every input you wish to retain. Unknown fields are rejected so frontend typos are visible.
- There is no automatic conversion from calorie balances to predicted weight loss or a Good/Check judgment.

## Frontend integration

The development origins `http://localhost:3000` and `http://localhost:5173` are allowed by CORS. For another origin, set `FRONTEND_ORIGINS` to a comma-separated list before starting.

```javascript
const response = await fetch('http://127.0.0.1:8080/api/v1/daily-logs/2026-11-12', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ weightKg: 72, activeCalories: 500, intakeCalories: 2000 })
});
const body = await response.json();
if (!response.ok) throw new Error(body.detail ?? 'Unable to save log');
```

Environment settings: `PORT` (8080), `DB_URL` (file H2), `DB_USERNAME` (sa), `DB_PASSWORD` (empty), and `FRONTEND_ORIGINS`. Relative database paths resolve against the directory where you launch the app. Back up the `data` directory while the app is stopped.

## Current scope

This version is a single-user local backend without login. It binds to loopback by default. Authentication, deployment, Excel import, goals, and frontend screens remain future work. CORS controls browser origins; it is not authentication.

## MVC project structure

```text
src/main/java/com/pranav/weighttracker/
├── WeightTrackerApplication.java
├── controller/
│   └── TrackerController.java
├── model/
│   ├── DailyLog.java
│   ├── EntryType.java
│   └── MonthlySettings.java
├── dto/
│   ├── LogInput.java
│   ├── LogView.java
│   ├── SettingsInput.java
│   └── WeeklySummary.java
├── service/
│   └── TrackerService.java
├── repository/
│   └── TrackerRepository.java
├── config/
│   └── ApiConfiguration.java
└── exception/
    └── ApiErrors.java
```

The controller accepts HTTP requests and returns response DTOs. The service owns validation, calculations, and transactions. The repository reads and writes domain models using parameterized SQL. Model records represent stored logs and monthly settings, while DTO records define API input and output shapes. Configuration and error handling have their own packages.

Request flow: frontend → controller → service → repository → database. Responses return through the service and controller as JSON. The frontend will provide the visual view, so this REST backend has no server-rendered templates. `LogView` is a JSON response DTO.

The application entry point remains in the root package so Spring discovers every layer. API URLs, JSON field names, database schema, and calculation behavior are unchanged by this refactor.

 `schema.sql` creates the initial tables without deleting data. Future schema changes need migrations rather than changes to CREATE TABLE alone. Repository SQL currently targets H2.

## Tests

Run `mvn test`. Integration tests use a separate in-memory database and cover CRUD, duplicate dates, historical settings snapshots, missing versus zero inputs, weekly boundaries, planned-entry exclusion, input errors, date ranges, and CORS. They do not modify the file database.
