package com.abes.hcl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "payroll_records", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payroll_employee_period", columnNames = {"employee_id", "salary_period"})
})
public class PayrollRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "salary_period", nullable = false)
    private LocalDate salaryPeriod;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal basicSalary;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal allowances;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deductions;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal netSalary;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20,
            columnDefinition = "varchar(20) default 'PENDING'")
    private PayrollPaymentStatus paymentStatus = PayrollPaymentStatus.PENDING;

    protected PayrollRecord() {
    }

    public PayrollRecord(Employee employee, LocalDate salaryPeriod, BigDecimal basicSalary,
                         BigDecimal allowances, BigDecimal deductions) {
        update(employee, salaryPeriod, basicSalary, allowances, deductions);
    }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public LocalDate getSalaryPeriod() { return salaryPeriod; }
    public BigDecimal getBasicSalary() { return basicSalary; }
    public BigDecimal getAllowances() { return allowances; }
    public BigDecimal getDeductions() { return deductions; }
    public BigDecimal getNetSalary() { return netSalary; }
    public PayrollPaymentStatus getPaymentStatus() { return paymentStatus; }

    public void setPaymentStatus(PayrollPaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public void update(Employee employee, LocalDate salaryPeriod, BigDecimal basicSalary,
                       BigDecimal allowances, BigDecimal deductions) {
        this.employee = employee;
        this.salaryPeriod = salaryPeriod.withDayOfMonth(1);
        this.basicSalary = basicSalary;
        this.allowances = allowances;
        this.deductions = deductions;
        this.netSalary = basicSalary.add(allowances).subtract(deductions);
    }
}
