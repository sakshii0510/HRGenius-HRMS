package com.abes.hcl.service;

import com.abes.hcl.dto.AttendanceRequest;
import com.abes.hcl.dto.AttendanceResponse;
import com.abes.hcl.entity.Attendance;
import com.abes.hcl.entity.AttendanceStatus;
import com.abes.hcl.entity.Employee;
import com.abes.hcl.repository.AttendanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeAccessService employeeAccess;

    public AttendanceService(AttendanceRepository attendanceRepository, EmployeeAccessService employeeAccess) {
        this.attendanceRepository = attendanceRepository;
        this.employeeAccess = employeeAccess;
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> search(Long employeeId, String email, LocalDate fromDate,
                                           LocalDate toDate, AttendanceStatus status) {
        validateDateRange(fromDate, toDate);
        return attendanceRepository.search(employeeId, email, fromDate, toDate, status)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AttendanceResponse get(Long id, String email, boolean admin) {
        Attendance attendance = admin
                ? attendanceRepository.findById(id).orElseThrow(() -> notFound("Attendance record", id))
                : attendanceRepository.findOwned(id, email).orElseThrow(() -> notFound("Attendance record", id));
        return toResponse(attendance);
    }

    public AttendanceResponse create(AttendanceRequest request) {
        Employee employee = employeeAccess.getEmployee(request.employeeId());
        validateRequest(request);
        return toResponse(attendanceRepository.save(new Attendance(
                employee, request.attendanceDate(), request.checkIn(), request.checkOut(), request.status())));
    }

    public AttendanceResponse update(Long id, AttendanceRequest request) {
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> notFound("Attendance record", id));
        Employee employee = employeeAccess.getEmployee(request.employeeId());
        validateRequest(request);
        attendance.update(employee, request.attendanceDate(), request.checkIn(), request.checkOut(), request.status());
        return toResponse(attendance);
    }

    public void delete(Long id) {
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> notFound("Attendance record", id));
        attendanceRepository.delete(attendance);
    }

    private void validateRequest(AttendanceRequest request) {
        if (request.checkIn() != null && request.checkOut() != null
                && !request.checkOut().isAfter(request.checkIn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check-out must be later than check-in");
        }
        boolean needsTimes = request.status() == AttendanceStatus.PRESENT
                || request.status() == AttendanceStatus.LATE
                || request.status() == AttendanceStatus.HALF_DAY;
        if (needsTimes && request.checkIn() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Check-in time is required for present, late, or half-day attendance");
        }
        if ((request.status() == AttendanceStatus.ABSENT || request.status() == AttendanceStatus.ON_LEAVE)
                && (request.checkIn() != null || request.checkOut() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Absent and on-leave records cannot include check-in or check-out times");
        }
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate must be on or before toDate");
        }
    }

    private AttendanceResponse toResponse(Attendance attendance) {
        Employee employee = attendance.getEmployee();
        return new AttendanceResponse(attendance.getId(), employee.getId(),
                employee.getFirstName() + " " + employee.getLastName(), attendance.getAttendanceDate(),
                attendance.getCheckIn(), attendance.getCheckOut(), attendance.getStatus());
    }

    private ResponseStatusException notFound(String type, Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " not found with ID: " + id);
    }
}
