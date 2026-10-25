package com.abes.hcl.dto;

import com.abes.hcl.entity.PayrollPaymentStatus;
import java.math.BigDecimal;

public record PayrollResponse(
        Long id,
        Long employeeId,
        String employeeName,
        String salaryPeriod,
        BigDecimal basicSalary,
        BigDecimal allowances,
        BigDecimal deductions,
        BigDecimal netSalary,
        PayrollPaymentStatus paymentStatus) {
}
