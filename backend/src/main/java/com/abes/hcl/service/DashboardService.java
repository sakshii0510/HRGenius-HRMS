package com.abes.hcl.service;

import com.abes.hcl.dto.DashboardSummary;
import com.abes.hcl.dto.PayrollMonthTotal;
import com.abes.hcl.entity.AttendanceStatus;
import com.abes.hcl.entity.JobStatus;
import com.abes.hcl.entity.LeaveStatus;
import com.abes.hcl.entity.WorkspaceModule;
import com.abes.hcl.repository.AttendanceRepository;
import com.abes.hcl.repository.EmployeeRepository;
import com.abes.hcl.repository.JobOpeningRepository;
import com.abes.hcl.repository.LeaveApplicationRepository;
import com.abes.hcl.repository.PayrollRepository;
import com.abes.hcl.repository.WorkspaceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DashboardService {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final EmployeeRepository employees;
    private final AttendanceRepository attendance;
    private final LeaveApplicationRepository leaves;
    private final PayrollRepository payroll;
    private final JobOpeningRepository jobs;
    private final WorkspaceRecordRepository workspace;
    private final EmployeeService employeeService;

    public DashboardService(EmployeeRepository employees, AttendanceRepository attendance,
                            LeaveApplicationRepository leaves, PayrollRepository payroll,
                            JobOpeningRepository jobs, WorkspaceRecordRepository workspace,
                            EmployeeService employeeService) {
        this.employees = employees;
        this.attendance = attendance;
        this.leaves = leaves;
        this.payroll = payroll;
        this.jobs = jobs;
        this.workspace = workspace;
        this.employeeService = employeeService;
    }

    @Transactional(readOnly = true)
    public DashboardSummary getSummary(String email, boolean admin) {
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.from(today);
        Long employeeId = admin ? null : employeeService.getEmployeeByEmail(email)
                .map(employee -> employee.getId()).orElse(-1L);
        List<PayrollMonthTotal> history = java.util.stream.IntStream.rangeClosed(0, 7)
                .mapToObj(offset -> month.minusMonths(7L - offset))
                .map(period -> new PayrollMonthTotal(period.format(PERIOD_FORMAT),
                        payroll.sumNetSalaryForPeriod(period.atDay(1), employeeId)))
                .toList();

        long present = countAttendance(today, email, admin, AttendanceStatus.PRESENT);
        long late = countAttendance(today, email, admin, AttendanceStatus.LATE);
        long absent = countAttendance(today, email, admin, AttendanceStatus.ABSENT);
        long attendanceTotal = admin
                ? attendance.countByAttendanceDate(today)
                : attendance.countForEmployeeOnDate(today, email);
        long pendingLeaves = admin
                ? leaves.countByStatus(LeaveStatus.PENDING)
                : employeeId < 0 ? 0 : leaves.countByStatusAndEmployee_Id(LeaveStatus.PENDING, employeeId);
        return new DashboardSummary(
                admin ? employees.count() : employeeId < 0 ? 0 : 1,
                payroll.sumNetSalaryForPeriod(month.atDay(1), employeeId),
                month.format(PERIOD_FORMAT),
                attendanceTotal,
                present,
                late,
                absent,
                pendingLeaves,
                jobs.countByStatus(JobStatus.OPEN),
                admin ? workspace.countByModuleAndStatusIgnoreCase(WorkspaceModule.TASKS, "TO_DO")
                        + workspace.countByModuleAndStatusIgnoreCase(WorkspaceModule.TASKS, "IN_PROGRESS")
                        : employeeId < 0 ? 0 : workspace.search(WorkspaceModule.TASKS, null, null,
                                null, true, employeeId).stream()
                                .filter(record -> !record.getStatus().equalsIgnoreCase("COMPLETED")).count(),
                workspace.countByModuleAndStatusIgnoreCase(WorkspaceModule.ANNOUNCEMENTS, "PUBLISHED"),
                history);
    }

    private long countAttendance(LocalDate date, String email, boolean admin, AttendanceStatus status) {
        return admin ? attendance.countByAttendanceDateAndStatus(date, status)
                : attendance.countForEmployeeOnDateByStatus(date, email, status);
    }
}
