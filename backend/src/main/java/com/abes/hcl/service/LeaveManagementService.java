package com.abes.hcl.service;

import com.abes.hcl.dto.LeaveApplicationRequest;
import com.abes.hcl.dto.LeaveApplicationResponse;
import com.abes.hcl.dto.LeaveReviewRequest;
import com.abes.hcl.entity.Employee;
import com.abes.hcl.entity.LeaveApplication;
import com.abes.hcl.entity.LeaveStatus;
import com.abes.hcl.repository.EmployeeRepository;
import com.abes.hcl.repository.LeaveApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class LeaveManagementService {

    private final LeaveApplicationRepository leaveRepository;
    private final EmployeeRepository employeeRepository;

    public LeaveManagementService(LeaveApplicationRepository leaveRepository, EmployeeRepository employeeRepository) {
        this.leaveRepository = leaveRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaveApplicationResponse> search(Long employeeId, String email, LeaveStatus status,
                                                 LocalDate fromDate, LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        return leaveRepository.search(employeeId, email, status, fromDate, toDate)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LeaveApplicationResponse getOwned(Long id, String email) {
        return toResponse(leaveRepository.findOwned(id, email)
                .orElseThrow(() -> notFound(id)));
    }

    @Transactional(readOnly = true)
    public LeaveApplicationResponse get(Long id) {
        return toResponse(leaveRepository.findWithEmployee(id).orElseThrow(() -> notFound(id)));
    }

    public LeaveApplicationResponse create(LeaveApplicationRequest request) {
        validateDates(request.startDate(), request.endDate());
        Employee employee = getEmployee(request.employeeId());
        return toResponse(leaveRepository.save(new LeaveApplication(
                employee, request.leaveType(), request.startDate(), request.endDate(), request.reason())));
    }

    public LeaveApplicationResponse updateOwned(Long id, String email, LeaveApplicationRequest request) {
        LeaveApplication application = leaveRepository.findOwned(id, email)
                .orElseThrow(() -> notFound(id));
        ensurePending(application);
        validateDates(request.startDate(), request.endDate());
        Employee employee = getEmployee(request.employeeId());
        if (!employee.getEmail().equalsIgnoreCase(email)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only apply for leave for your own employee record");
        }
        application.update(request.leaveType(), request.startDate(), request.endDate(), request.reason());
        return toResponse(application);
    }

    public void deleteOwned(Long id, String email) {
        LeaveApplication application = leaveRepository.findOwned(id, email)
                .orElseThrow(() -> notFound(id));
        ensurePending(application);
        leaveRepository.delete(application);
    }

    public LeaveApplicationResponse review(Long id, LeaveReviewRequest request) {
        if (request.status() != LeaveStatus.APPROVED && request.status() != LeaveStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A leave request can only be approved or rejected");
        }
        LeaveApplication application = leaveRepository.findById(id).orElseThrow(() -> notFound(id));
        ensurePending(application);
        application.review(request.status(), request.reviewComment());
        return toResponse(application);
    }

    private Employee getEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Employee not found with ID: " + id));
    }

    private void ensurePending(LeaveApplication application) {
        if (application.getStatus() != LeaveStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending leave applications can be changed");
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be on or before endDate");
        }
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate must be on or before toDate");
        }
    }

    private LeaveApplicationResponse toResponse(LeaveApplication application) {
        Employee employee = application.getEmployee();
        return new LeaveApplicationResponse(application.getId(), employee.getId(),
                employee.getFirstName() + " " + employee.getLastName(), application.getLeaveType(),
                application.getStartDate(), application.getEndDate(), application.getReason(),
                application.getStatus(), application.getAppliedAt(), application.getReviewedAt(),
                application.getReviewComment());
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave application not found with ID: " + id);
    }
}
