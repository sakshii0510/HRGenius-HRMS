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

import java.time.LocalDateTime;

@Entity
@Table(name = "job_applications", uniqueConstraints = {
        @UniqueConstraint(name = "uk_job_candidate", columnNames = {"job_opening_id", "candidate_id"})
})
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_opening_id", nullable = false)
    private JobOpening jobOpening;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApplicationStatus status;

    @Column(length = 3000)
    private String coverLetter;

    @Column(nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    protected JobApplication() {
    }

    public JobApplication(JobOpening jobOpening, Candidate candidate, String coverLetter) {
        this.jobOpening = jobOpening;
        this.candidate = candidate;
        this.coverLetter = coverLetter == null || coverLetter.isBlank() ? null : coverLetter.trim();
        this.status = ApplicationStatus.APPLIED;
        this.appliedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public JobOpening getJobOpening() { return jobOpening; }
    public Candidate getCandidate() { return candidate; }
    public ApplicationStatus getStatus() { return status; }
    public String getCoverLetter() { return coverLetter; }
    public LocalDateTime getAppliedAt() { return appliedAt; }

    public void changeStatus(ApplicationStatus status) {
        this.status = status;
    }
}
