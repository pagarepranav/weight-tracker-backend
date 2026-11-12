package com.pranav.weighttracker.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.pranav.weighttracker.model.EntryType;

public record LogView(LocalDate date, EntryType entryType, BigDecimal weightKg,
                          BigDecimal activeCalories, BigDecimal intakeCalories,
                          BigDecimal targetCalories, BigDecimal sedentaryCalories,
                          BigDecimal totalAllowedCalories, BigDecimal budgetBalanceCalories,
                          BigDecimal estimatedEnergyDeficitCalories, String notes) {}
