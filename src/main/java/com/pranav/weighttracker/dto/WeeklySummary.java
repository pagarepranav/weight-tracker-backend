package com.pranav.weighttracker.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeeklySummary(LocalDate weekStart, LocalDate weekEnd, LocalDate periodStart,
                                LocalDate periodEnd, int actualLogDays, int weighInDays,
                                int completeCalorieDays, BigDecimal averageWeightKg,
                                BigDecimal averageIntakeCalories, BigDecimal averageActiveCalories,
                                BigDecimal totalBudgetBalanceCalories,
                                BigDecimal totalEstimatedEnergyDeficitCalories) {}
