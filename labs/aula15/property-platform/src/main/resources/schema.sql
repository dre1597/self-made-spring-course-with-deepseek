CREATE TABLE monthly_report_lines (
    visit_id BIGINT PRIMARY KEY,
    report_month VARCHAR(7) NOT NULL,
    property_id BIGINT NOT NULL,
    visitor_name VARCHAR(255) NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL
);