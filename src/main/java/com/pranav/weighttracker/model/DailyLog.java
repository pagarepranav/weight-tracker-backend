package com.pranav.weighttracker.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyLog(LocalDate date, EntryType entryType, BigDecimal weightKg,
                           BigDecimal activeCalories, BigDecimal intakeCalories,
                           BigDecimal targetCalories, BigDecimal sedentaryCalories, String notes) {}
