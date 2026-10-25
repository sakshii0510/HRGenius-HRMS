package com.abes.hcl.repository;

import com.abes.hcl.entity.Attendance;
import com.abes.hcl.entity.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    long countByAttendanceDate(LocalDate attendanceDate);

    long countByAttendanceDateAndStatus(LocalDate attendanceDate, AttendanceStatus status);

    @Query("""
            select count(a) from Attendance a
            where a.attendanceDate = :date and lower(a.employee.email) = lower(:email)
            """)
    long countForEmployeeOnDate(@Param("date") LocalDate date, @Param("email") String email);

    @Query("""
            select count(a) from Attendance a
            where a.attendanceDate = :date and lower(a.employee.email) = lower(:email)
              and a.status = :status
            """)
    long countForEmployeeOnDateByStatus(@Param("date") LocalDate date, @Param("email") String email,
                                        @Param("status") AttendanceStatus status);

    @Query("""
            select a from Attendance a
            where (:employeeId is null or a.employee.id = :employeeId)
              and (:email is null or lower(a.employee.email) = lower(:email))
              and (:fromDate is null or a.attendanceDate >= :fromDate)
              and (:toDate is null or a.attendanceDate <= :toDate)
              and (:status is null or a.status = :status)
            order by a.attendanceDate desc, a.id desc
            """)
    List<Attendance> search(@Param("employeeId") Long employeeId,
                            @Param("email") String email,
                            @Param("fromDate") LocalDate fromDate,
                            @Param("toDate") LocalDate toDate,
                            @Param("status") AttendanceStatus status);

    @Query("""
            select a from Attendance a
            where a.id = :id and lower(a.employee.email) = lower(:email)
            """)
    Optional<Attendance> findOwned(@Param("id") Long id, @Param("email") String email);
}
