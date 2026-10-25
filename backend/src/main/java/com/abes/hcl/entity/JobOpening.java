package com.abes.hcl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "job_openings")
public class JobOpening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 100)
    private String department;

    @Column(nullable = false, length = 100)
    private String location;

    @Column(nullable = false, length = 40)
    private String employmentType;

    @Column(nullable = false, length = 5000)
    private String description;

    @Column(precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDate postedDate;

    protected JobOpening() {
    }

    public JobOpening(String title, String department, String location, String employmentType,
                      String description, BigDecimal salaryMin, BigDecimal salaryMax, JobStatus status) {
        update(title, department, location, employmentType, description, salaryMin, salaryMax, status);
        this.postedDate = LocalDate.now();
    }

    @PrePersist
    void onCreate() {
        if (postedDate == null) postedDate = LocalDate.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDepartment() { return department; }
    public String getLocation() { return location; }
    public String getEmploymentType() { return employmentType; }
    public String getDescription() { return description; }
    public BigDecimal getSalaryMin() { return salaryMin; }
    public BigDecimal getSalaryMax() { return salaryMax; }
    public JobStatus getStatus() { return status; }
    public LocalDate getPostedDate() { return postedDate; }

    public void update(String title, String department, String location, String employmentType,
                       String description, BigDecimal salaryMin, BigDecimal salaryMax, JobStatus status) {
        this.title = title.trim();
        this.department = department.trim();
        this.location = location.trim();
        this.employmentType = employmentType.trim();
        this.description = description.trim();
        this.salaryMin = salaryMin;
        this.salaryMax = salaryMax;
        this.status = status;
    }
}
