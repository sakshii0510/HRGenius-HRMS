package com.abes.hcl.service;

import com.abes.hcl.entity.Employee;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmployeeAccessService {

    private final EmployeeService employeeService;

    public EmployeeAccessService(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    public Employee getEmployee(Long employeeId) {
        return employeeService.getEmployeeById(employeeId);
    }

    public Employee getEmployeeForUser(Long employeeId, Authentication authentication) {
        Employee employee = getEmployee(employeeId);
        if (!isAdmin(authentication) && !employee.getEmail().equalsIgnoreCase(authentication.getName())) {
            throw new AccessDeniedException("You can only manage your own employee record");
        }
        return employee;
    }

    public Employee getOwnEmployee(String email) {
        return employeeService.getEmployeeByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "No employee profile is linked to this account. Ask an administrator to add your employee profile."));
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
