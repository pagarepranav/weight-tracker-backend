CREATE TABLE IF NOT EXISTS monthly_settings (
    month_key VARCHAR(7) PRIMARY KEY,
    sedentary_calories DECIMAL(10,2) NOT NULL CHECK (sedentary_calories > 0),
    target_calories DECIMAL(10,2) NOT NULL CHECK (target_calories > 0)
);
CREATE TABLE IF NOT EXISTS daily_logs (
    log_date DATE PRIMARY KEY,
    entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('ACTUAL', 'PLANNED')),
    weight_kg DECIMAL(6,2) CHECK (weight_kg > 0),
    active_calories DECIMAL(10,2) CHECK (active_calories >= 0),
    intake_calories DECIMAL(10,2) CHECK (intake_calories >= 0),
    target_calories DECIMAL(10,2),
    sedentary_calories DECIMAL(10,2),
    notes VARCHAR(2000)
);
