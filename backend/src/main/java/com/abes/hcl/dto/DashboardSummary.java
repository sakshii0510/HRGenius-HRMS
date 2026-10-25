package com.abes.hcl.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummary(
        long employeeCount,
        BigDecimal currentPayrollTotal,
        String payrollPeriod,
        long attendanceTotalToday,
        long attendancePresentToday,
        long attendanceLateToday,
        long attendanceAbsentToday,
        long pendingLeaveCount,
        long openJobCount,
        long openTaskCount,
        long publishedAnnouncementCount,
        List<PayrollMonthTotal> payrollHistory) {
}
