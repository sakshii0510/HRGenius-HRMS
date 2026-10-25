package com.abes.hcl.controller;

import com.abes.hcl.dto.JobApplicationRequest;
import com.abes.hcl.dto.JobApplicationResponse;
import com.abes.hcl.dto.JobApplicationStatusRequest;
import com.abes.hcl.dto.CandidateRequest;
import com.abes.hcl.dto.CandidateResponse;
import com.abes.hcl.dto.JobOpeningRequest;
import com.abes.hcl.dto.JobOpeningResponse;
import com.abes.hcl.entity.ApplicationStatus;
import com.abes.hcl.entity.JobStatus;
import com.abes.hcl.service.EmployeeAccessService;
import com.abes.hcl.service.RecruitmentService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@RestController
@RequestMapping("/api/recruitment")
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class RecruitmentController {

    private final RecruitmentService recruitmentService;
    private final EmployeeAccessService employeeAccess;

    public RecruitmentController(RecruitmentService recruitmentService, EmployeeAccessService employeeAccess) {
        this.recruitmentService = recruitmentService;
        this.employeeAccess = employeeAccess;
    }

    @GetMapping("/jobs")
    public List<JobOpeningResponse> searchJobs(Authentication authentication,
                                               @RequestParam(required = false) JobStatus status,
                                               @RequestParam(required = false) String search) {
        return recruitmentService.searchJobs(status, search, employeeAccess.isAdmin(authentication));
    }

    @GetMapping("/jobs/{id}")
    public JobOpeningResponse getJob(@PathVariable Long id, Authentication authentication) {
        return recruitmentService.getJob(id, employeeAccess.isAdmin(authentication));
    }

    @PostMapping("/jobs")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public JobOpeningResponse createJob(@Valid @RequestBody JobOpeningRequest request) {
        return recruitmentService.createJob(request);
    }

    @PutMapping("/jobs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public JobOpeningResponse updateJob(@PathVariable Long id, @Valid @RequestBody JobOpeningRequest request) {
        return recruitmentService.updateJob(id, request);
    }

    @DeleteMapping("/jobs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJob(@PathVariable Long id) {
        recruitmentService.deleteJob(id);
    }

    @GetMapping("/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CandidateResponse> searchCandidates(@RequestParam(required = false) String search) {
        return recruitmentService.searchCandidates(search);
    }

    @GetMapping("/candidates/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CandidateResponse getCandidate(@PathVariable Long id) {
        return recruitmentService.getCandidate(id);
    }

    @PostMapping("/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateResponse createCandidate(@Valid @RequestBody CandidateRequest request) {
        return recruitmentService.createCandidate(request);
    }

    @PutMapping("/candidates/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CandidateResponse updateCandidate(@PathVariable Long id, @Valid @RequestBody CandidateRequest request) {
        return recruitmentService.updateCandidate(id, request);
    }

    @DeleteMapping("/candidates/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCandidate(@PathVariable Long id) {
        recruitmentService.deleteCandidate(id);
    }

    @PostMapping("/jobs/{jobId}/applications")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @ResponseStatus(HttpStatus.CREATED)
    public JobApplicationResponse apply(@PathVariable Long jobId,
                                        @Valid @RequestBody JobApplicationRequest request,
                                        Authentication authentication) {
        return recruitmentService.apply(jobId, authentication.getName(), request);
    }

    @GetMapping("/applications")
    public List<JobApplicationResponse> searchApplications(Authentication authentication,
                                                           @RequestParam(required = false) Long jobId,
                                                           @RequestParam(required = false) ApplicationStatus status,
                                                           @RequestParam(required = false) String search) {
        boolean admin = employeeAccess.isAdmin(authentication);
        return recruitmentService.searchApplications(jobId, status, admin ? null : authentication.getName(), search);
    }

    @GetMapping("/applications/{id}")
    public JobApplicationResponse getApplication(@PathVariable Long id, Authentication authentication) {
        return recruitmentService.getApplication(id, authentication.getName(),
                employeeAccess.isAdmin(authentication));
    }

    @PutMapping("/applications/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public JobApplicationResponse updateApplicationStatus(
            @PathVariable Long id, @Valid @RequestBody JobApplicationStatusRequest request) {
        return recruitmentService.updateApplicationStatus(id, request.status());
    }

    @DeleteMapping("/applications/{id}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdrawApplication(@PathVariable Long id, Authentication authentication) {
        recruitmentService.withdrawApplication(id, authentication.getName());
    }
}
