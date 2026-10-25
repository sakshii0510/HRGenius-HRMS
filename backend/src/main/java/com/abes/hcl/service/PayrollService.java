package com.abes.hcl.service;

import com.abes.hcl.dto.PayrollRequest;
import com.abes.hcl.dto.PayrollResponse;
import com.abes.hcl.entity.Employee;
import com.abes.hcl.entity.PayrollRecord;
import com.abes.hcl.repository.EmployeeRepository;
import com.abes.hcl.repository.PayrollRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@Transactional
public class PayrollService {

    private static final BigDecimal MAX_STORED_AMOUNT = new BigDecimal("9999999999.99");

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;

    public PayrollService(PayrollRepository payrollRepository, EmployeeRepository employeeRepository) {
        this.payrollRepository = payrollRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<PayrollResponse> search(Long employeeId, String email, String fromPeriod, String toPeriod) {
        LocalDate fromDate = parsePeriod(fromPeriod);
        LocalDate toDate = parsePeriod(toPeriod);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromPeriod must be on or before toPeriod");
        }
        return payrollRepository.search(employeeId, email, fromDate, toDate)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PayrollResponse get(Long id, String email, boolean admin) {
        PayrollRecord record = admin
                ? payrollRepository.findById(id).orElseThrow(() -> notFound(id))
                : payrollRepository.findOwned(id, email).orElseThrow(() -> notFound(id));
        return toResponse(record);
    }

    public PayrollResponse create(PayrollRequest request) {
        validateAmounts(request);
        Employee employee = getEmployee(request.employeeId());
        PayrollRecord record = new PayrollRecord(
                employee, parsePeriodRequired(request.salaryPeriod()), request.basicSalary(),
                request.allowances(), request.deductions());
        if (request.paymentStatus() != null) {
            record.setPaymentStatus(request.paymentStatus());
        }
        return toResponse(payrollRepository.save(record));
    }

    public PayrollResponse update(Long id, PayrollRequest request) {
        validateAmounts(request);
        PayrollRecord record = payrollRepository.findById(id).orElseThrow(() -> notFound(id));
        record.update(getEmployee(request.employeeId()), parsePeriodRequired(request.salaryPeriod()),
                request.basicSalary(), request.allowances(), request.deductions());
        if (request.paymentStatus() != null) {
            record.setPaymentStatus(request.paymentStatus());
        }
        return toResponse(payrollRepository.save(record));
    }

    public void delete(Long id) {
        PayrollRecord record = payrollRepository.findById(id).orElseThrow(() -> notFound(id));
        payrollRepository.delete(record);
    }

    private void validateAmounts(PayrollRequest request) {
        parsePeriodRequired(request.salaryPeriod());
        BigDecimal gross = request.basicSalary().add(request.allowances());
        if (gross.compareTo(MAX_STORED_AMOUNT) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Basic salary plus allowances exceeds the supported payroll amount");
        }
        if (request.deductions().compareTo(gross) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Deductions cannot exceed basic salary plus allowances");
        }
    }

    private LocalDate parsePeriod(String value) {
        return value == null || value.isBlank() ? null : parsePeriodRequired(value);
    }

    private LocalDate parsePeriodRequired(String value) {
        try {
            return YearMonth.parse(value).atDay(1);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Salary period must use YYYY-MM format");
        }
    }

    private Employee getEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Employee not found with ID: " + id));
    }

    private PayrollResponse toResponse(PayrollRecord record) {
        Employee employee = record.getEmployee();
        return new PayrollResponse(record.getId(), employee.getId(),
                employee.getFirstName() + " " + employee.getLastName(),
                YearMonth.from(record.getSalaryPeriod()).toString(), record.getBasicSalary(),
                record.getAllowances(), record.getDeductions(), record.getNetSalary(),
                record.getPaymentStatus());
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll record not found with ID: " + id);
    }
}
