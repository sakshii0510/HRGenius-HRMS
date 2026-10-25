package com.abes.hcl.service;

import com.abes.hcl.dto.JobApplicationRequest;
import com.abes.hcl.dto.JobApplicationResponse;
import com.abes.hcl.dto.CandidateRequest;
import com.abes.hcl.dto.CandidateResponse;
import com.abes.hcl.dto.JobOpeningRequest;
import com.abes.hcl.dto.JobOpeningResponse;
import com.abes.hcl.entity.ApplicationStatus;
import com.abes.hcl.entity.Candidate;
import com.abes.hcl.entity.JobApplication;
import com.abes.hcl.entity.JobOpening;
import com.abes.hcl.entity.JobStatus;
import com.abes.hcl.repository.CandidateRepository;
import com.abes.hcl.repository.JobApplicationRepository;
import com.abes.hcl.repository.JobOpeningRepository;
import com.abes.hcl.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class RecruitmentService {

    private final JobOpeningRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final JobApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    public RecruitmentService(JobOpeningRepository jobRepository,
                              CandidateRepository candidateRepository,
                              JobApplicationRepository applicationRepository,
                              UserRepository userRepository) {
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<JobOpeningResponse> searchJobs(JobStatus status, String search, boolean admin) {
        JobStatus effectiveStatus = admin ? status : JobStatus.OPEN;
        return jobRepository.search(effectiveStatus, normalize(search)).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public JobOpeningResponse getJob(Long id, boolean admin) {
        JobOpening job = findJob(id);
        if (!admin && job.getStatus() != JobStatus.OPEN) {
            throw notFoundJob(id);
        }
        return toResponse(job);
    }

    public JobOpeningResponse createJob(JobOpeningRequest request) {
        validateSalaryRange(request);
        return toResponse(jobRepository.save(new JobOpening(
                request.title(), request.department(), request.location(), request.employmentType(),
                request.description(), request.salaryMin(), request.salaryMax(), request.status())));
    }

    public JobOpeningResponse updateJob(Long id, JobOpeningRequest request) {
        validateSalaryRange(request);
        JobOpening job = findJob(id);
        job.update(request.title(), request.department(), request.location(), request.employmentType(),
                request.description(), request.salaryMin(), request.salaryMax(), request.status());
        return toResponse(jobRepository.save(job));
    }

    public void deleteJob(Long id) {
        jobRepository.delete(findJob(id));
    }

    @Transactional(readOnly = true)
    public List<CandidateResponse> searchCandidates(String search) {
        return candidateRepository.search(normalize(search)).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CandidateResponse getCandidate(Long id) {
        return toResponse(candidateRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found with ID: " + id)));
    }

    public CandidateResponse createCandidate(CandidateRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (candidateRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A candidate with this email already exists");
        }
        return toResponse(candidateRepository.save(new Candidate(request.name(), email, request.phone())));
    }

    public CandidateResponse updateCandidate(Long id, CandidateRequest request) {
        Candidate candidate = candidateRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found with ID: " + id));
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        candidateRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "A candidate with this email already exists");
                });
        candidate.update(request.name(), email, request.phone());
        return toResponse(candidateRepository.save(candidate));
    }

    public void deleteCandidate(Long id) {
        Candidate candidate = candidateRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found with ID: " + id));
        candidateRepository.delete(candidate);
    }

    public JobApplicationResponse apply(Long jobId, String userEmail, JobApplicationRequest request) {
        JobOpening job = findJob(jobId);
        if (job.getStatus() != JobStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Applications are closed for this position");
        }
        var user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
        Candidate candidate = candidateRepository.findByEmailIgnoreCase(user.getEmail())
                .orElseGet(() -> candidateRepository.save(new Candidate(user.getName(), user.getEmail(), request.phone())));
        if (applicationRepository.existsByJobOpeningIdAndCandidateEmailIgnoreCase(jobId, user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already applied for this position");
        }
        candidate.update(user.getName(), user.getEmail(), request.phone());
        return toResponse(applicationRepository.save(new JobApplication(job, candidate, request.coverLetter())));
    }

    @Transactional(readOnly = true)
    public List<JobApplicationResponse> searchApplications(Long jobId, ApplicationStatus status,
                                                           String email, String search) {
        return applicationRepository.search(jobId, status, email, normalize(search))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public JobApplicationResponse getApplication(Long id, String email, boolean admin) {
        JobApplication application = applicationRepository.findById(id).orElseThrow(() -> notFoundApplication(id));
        if (!admin && !application.getCandidate().getEmail().equalsIgnoreCase(email)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only view your own applications");
        }
        return toResponse(application);
    }

    public JobApplicationResponse updateApplicationStatus(Long id, ApplicationStatus status) {
        JobApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> notFoundApplication(id));
        if (!isAllowedTransition(application.getStatus(), status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Application status cannot change from " + application.getStatus() + " to " + status);
        }
        application.changeStatus(status);
        return toResponse(application);
    }

    public void withdrawApplication(Long id, String email) {
        JobApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> notFoundApplication(id));
        if (!application.getCandidate().getEmail().equalsIgnoreCase(email)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only withdraw your own applications");
        }
        if (application.getStatus() != ApplicationStatus.APPLIED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only applications that have not entered review can be withdrawn");
        }
        application.changeStatus(ApplicationStatus.WITHDRAWN);
    }

    private boolean isAllowedTransition(ApplicationStatus current, ApplicationStatus next) {
        return switch (current) {
            case APPLIED -> next == ApplicationStatus.SCREENING || next == ApplicationStatus.REJECTED;
            case SCREENING -> next == ApplicationStatus.INTERVIEW
                    || next == ApplicationStatus.OFFERED || next == ApplicationStatus.REJECTED;
            case INTERVIEW -> next == ApplicationStatus.OFFERED || next == ApplicationStatus.REJECTED;
            case OFFERED, REJECTED, WITHDRAWN -> false;
        };
    }

    private void validateSalaryRange(JobOpeningRequest request) {
        if (request.salaryMin() != null && request.salaryMax() != null
                && request.salaryMin().compareTo(request.salaryMax()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "salaryMin must be less than or equal to salaryMax");
        }
    }

    private JobOpening findJob(Long id) {
        return jobRepository.findById(id).orElseThrow(() -> notFoundJob(id));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private JobOpeningResponse toResponse(JobOpening job) {
        return new JobOpeningResponse(job.getId(), job.getTitle(), job.getDepartment(), job.getLocation(),
                job.getEmploymentType(), job.getDescription(), job.getSalaryMin(), job.getSalaryMax(),
                job.getStatus(), job.getPostedDate());
    }

    private JobApplicationResponse toResponse(JobApplication application) {
        Candidate candidate = application.getCandidate();
        return new JobApplicationResponse(application.getId(), application.getJobOpening().getId(),
                application.getJobOpening().getTitle(), candidate.getId(), candidate.getName(),
                candidate.getEmail(), candidate.getPhone(), application.getStatus(),
                application.getCoverLetter(), application.getAppliedAt());
    }

    private CandidateResponse toResponse(Candidate candidate) {
        return new CandidateResponse(candidate.getId(), candidate.getName(),
                candidate.getEmail(), candidate.getPhone());
    }

    private ResponseStatusException notFoundJob(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Job opening not found with ID: " + id);
    }

    private ResponseStatusException notFoundApplication(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Job application not found with ID: " + id);
    }
}
