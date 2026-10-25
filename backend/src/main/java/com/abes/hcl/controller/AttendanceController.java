package com.abes.hcl.controller;

import com.abes.hcl.dto.AttendanceRequest;
import com.abes.hcl.dto.AttendanceResponse;
import com.abes.hcl.entity.AttendanceStatus;
import com.abes.hcl.service.AttendanceService;
import com.abes.hcl.service.EmployeeAccessService;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final EmployeeAccessService employeeAccess;

    public AttendanceController(AttendanceService attendanceService, EmployeeAccessService employeeAccess) {
        this.attendanceService = attendanceService;
        this.employeeAccess = employeeAccess;
    }

    @GetMapping
    public List<AttendanceResponse> search(Authentication authentication,
                                           @RequestParam(required = false) Long employeeId,
                                           @RequestParam(required = false) LocalDate fromDate,
                                           @RequestParam(required = false) LocalDate toDate,
                                           @RequestParam(required = false) AttendanceStatus status) {
        boolean admin = employeeAccess.isAdmin(authentication);
        if (!admin && employeeId != null
                && !employeeAccess.getEmployeeForUser(employeeId, authentication).getEmail()
                .equalsIgnoreCase(authentication.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("You can only view your own attendance");
        }
        return attendanceService.search(admin ? employeeId : null,
                admin ? null : authentication.getName(), fromDate, toDate, status);
    }

    @GetMapping("/{id}")
    public AttendanceResponse get(@PathVariable Long id, Authentication authentication) {
        return attendanceService.get(id, authentication.getName(), employeeAccess.isAdmin(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceResponse create(@Valid @RequestBody AttendanceRequest request, Authentication authentication) {
        employeeAccess.getEmployeeForUser(request.employeeId(), authentication);
        return attendanceService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AttendanceResponse update(@PathVariable Long id, @Valid @RequestBody AttendanceRequest request) {
        return attendanceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        attendanceService.delete(id);
    }
}
