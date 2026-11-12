package com.pranav.weighttracker.dto;

import java.math.BigDecimal;
import com.pranav.weighttracker.model.EntryType;

// PUT replaces the entire input. Omitted optional values become null.
public record LogInput(EntryType entryType, BigDecimal weightKg, BigDecimal activeCalories,
                           BigDecimal intakeCalories, String notes) {}
