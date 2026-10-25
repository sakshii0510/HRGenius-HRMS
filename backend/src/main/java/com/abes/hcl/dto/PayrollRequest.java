package com.abes.hcl.dto;

import com.abes.hcl.entity.PayrollPaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PayrollRequest(
        @NotNull @Positive Long employeeId,
        @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "Salary period must use YYYY-MM format")
        String salaryPeriod,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal basicSalary,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal allowances,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal deductions,
        PayrollPaymentStatus paymentStatus) {
}
