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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_applications")
public class LeaveApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LeaveType leaveType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LeaveStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    private LocalDateTime reviewedAt;

    @Column(length = 500)
    private String reviewComment;

    protected LeaveApplication() {
    }

    public LeaveApplication(Employee employee, LeaveType leaveType, LocalDate startDate,
                            LocalDate endDate, String reason) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason.trim();
        this.status = LeaveStatus.PENDING;
        this.appliedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public LeaveType getLeaveType() { return leaveType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getReason() { return reason; }
    public LeaveStatus getStatus() { return status; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public String getReviewComment() { return reviewComment; }

    public void update(LeaveType leaveType, LocalDate startDate, LocalDate endDate, String reason) {
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason.trim();
    }

    public void review(LeaveStatus status, String reviewComment) {
        this.status = status;
        this.reviewComment = reviewComment == null || reviewComment.isBlank()
                ? null
                : reviewComment.trim();
        this.reviewedAt = LocalDateTime.now();
    }
}
