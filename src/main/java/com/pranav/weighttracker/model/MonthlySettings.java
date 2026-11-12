package com.pranav.weighttracker.model;

import java.math.BigDecimal;

public record MonthlySettings(String month, BigDecimal sedentaryCalories, BigDecimal targetCalories) {}
