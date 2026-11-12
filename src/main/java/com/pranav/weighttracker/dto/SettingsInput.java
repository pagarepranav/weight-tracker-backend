package com.pranav.weighttracker.dto;

import java.math.BigDecimal;

public record SettingsInput(BigDecimal sedentaryCalories, BigDecimal targetCalories) {}
