package com.abes.hcl.controller;

import com.abes.hcl.dto.PayrollRequest;
import com.abes.hcl.dto.PayrollResponse;
import com.abes.hcl.service.EmployeeAccessService;
import com.abes.hcl.service.PayrollService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payroll")
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class PayrollController {

    private final PayrollService payrollService;
    private final EmployeeAccessService employeeAccess;

    public PayrollController(PayrollService payrollService, EmployeeAccessService employeeAccess) {
        this.payrollService = payrollService;
        this.employeeAccess = employeeAccess;
    }

    @GetMapping
    public List<PayrollResponse> search(Authentication authentication,
                                        @RequestParam(required = false) Long employeeId,
                                        @RequestParam(required = false) String fromPeriod,
                                        @RequestParam(required = false) String toPeriod) {
        boolean admin = employeeAccess.isAdmin(authentication);
        if (!admin && employeeId != null) employeeAccess.getEmployeeForUser(employeeId, authentication);
        return payrollService.search(admin ? employeeId : null,
                admin ? null : authentication.getName(), fromPeriod, toPeriod);
    }

    @GetMapping("/{id}")
    public PayrollResponse get(@PathVariable Long id, Authentication authentication) {
        return payrollService.get(id, authentication.getName(), employeeAccess.isAdmin(authentication));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollResponse create(@Valid @RequestBody PayrollRequest request) {
        return payrollService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PayrollResponse update(@PathVariable Long id, @Valid @RequestBody PayrollRequest request) {
        return payrollService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        payrollService.delete(id);
    }
}
