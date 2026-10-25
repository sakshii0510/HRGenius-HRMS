package com.abes.hcl.controller;

import com.abes.hcl.dto.LeaveApplicationRequest;
import com.abes.hcl.dto.LeaveApplicationResponse;
import com.abes.hcl.dto.LeaveReviewRequest;
import com.abes.hcl.entity.LeaveStatus;
import com.abes.hcl.service.EmployeeAccessService;
import com.abes.hcl.service.LeaveManagementService;
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
@RequestMapping("/api/leave-applications")
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class LeaveManagementController {

    private final LeaveManagementService leaveService;
    private final EmployeeAccessService employeeAccess;

    public LeaveManagementController(LeaveManagementService leaveService, EmployeeAccessService employeeAccess) {
        this.leaveService = leaveService;
        this.employeeAccess = employeeAccess;
    }

    @GetMapping
    public List<LeaveApplicationResponse> search(Authentication authentication,
                                                 @RequestParam(required = false) Long employeeId,
                                                 @RequestParam(required = false) LeaveStatus status,
                                                 @RequestParam(required = false) LocalDate fromDate,
                                                 @RequestParam(required = false) LocalDate toDate) {
        if (employeeAccess.isAdmin(authentication)) {
            return leaveService.search(employeeId, null, status, fromDate, toDate);
        }
        if (employeeId != null) employeeAccess.getEmployeeForUser(employeeId, authentication);
        return leaveService.search(null, authentication.getName(), status, fromDate, toDate);
    }

    @GetMapping("/{id}")
    public LeaveApplicationResponse get(@PathVariable Long id, Authentication authentication) {
        if (employeeAccess.isAdmin(authentication)) {
            return leaveService.get(id);
        }
        return leaveService.getOwned(id, authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveApplicationResponse create(@Valid @RequestBody LeaveApplicationRequest request,
                                           Authentication authentication) {
        employeeAccess.getEmployeeForUser(request.employeeId(), authentication);
        return leaveService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public LeaveApplicationResponse update(@PathVariable Long id,
                                           @Valid @RequestBody LeaveApplicationRequest request,
                                           Authentication authentication) {
        return leaveService.updateOwned(id, authentication.getName(), request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        leaveService.deleteOwned(id, authentication.getName());
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public LeaveApplicationResponse review(@PathVariable Long id,
                                           @Valid @RequestBody LeaveReviewRequest request) {
        return leaveService.review(id, request);
    }
}
