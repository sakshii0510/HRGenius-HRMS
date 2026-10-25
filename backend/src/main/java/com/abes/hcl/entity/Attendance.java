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

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "attendance_records", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attendance_employee_date", columnNames = {"employee_id", "attendance_date"})
})
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    private LocalTime checkIn;
    private LocalTime checkOut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AttendanceStatus status;

    protected Attendance() {
    }

    public Attendance(Employee employee, LocalDate attendanceDate, LocalTime checkIn,
                      LocalTime checkOut, AttendanceStatus status) {
        this.employee = employee;
        this.attendanceDate = attendanceDate;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.status = status;
    }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public LocalTime getCheckIn() { return checkIn; }
    public LocalTime getCheckOut() { return checkOut; }
    public AttendanceStatus getStatus() { return status; }

    public void update(Employee employee, LocalDate attendanceDate, LocalTime checkIn,
                       LocalTime checkOut, AttendanceStatus status) {
        this.employee = employee;
        this.attendanceDate = attendanceDate;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.status = status;
    }
}
