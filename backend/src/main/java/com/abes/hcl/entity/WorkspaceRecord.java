package com.abes.hcl.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "workspace_records")
public class WorkspaceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WorkspaceModule module;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 5000)
    private String description;

    @Column(nullable = false, length = 32)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private LocalDate dueDate;

    private Integer score;

    @Column(nullable = false)
    private boolean visibleToEmployees;

    @Column(length = 255)
    private String fileName;

    @Column(length = 120)
    private String contentType;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "file_content")
    private byte[] fileContent;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected WorkspaceRecord() {
    }

    public WorkspaceRecord(WorkspaceModule module, String title, String description, String status,
                           Employee employee, LocalDate dueDate, Integer score, boolean visibleToEmployees) {
        this.module = module;
        update(title, description, status, employee, dueDate, score, visibleToEmployees);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void update(String title, String description, String status, Employee employee,
                       LocalDate dueDate, Integer score, boolean visibleToEmployees) {
        this.title = title;
        this.description = description;
        this.status = status;
        this.employee = employee;
        this.dueDate = dueDate;
        this.score = score;
        this.visibleToEmployees = visibleToEmployees;
    }

    public void attachFile(String fileName, String contentType, byte[] content) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileContent = content.clone();
    }

    public Long getId() { return id; }
    public WorkspaceModule getModule() { return module; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public Employee getEmployee() { return employee; }
    public LocalDate getDueDate() { return dueDate; }
    public Integer getScore() { return score; }
    public boolean isVisibleToEmployees() { return visibleToEmployees; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public byte[] getFileContent() { return fileContent == null ? null : fileContent.clone(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public long getFileSize() { return fileContent == null ? 0 : fileContent.length; }

    public void updateStatus(String status) {
        this.status = status;
    }
}
