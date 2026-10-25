package com.abes.hcl.repository;

import com.abes.hcl.entity.LeaveApplication;
import com.abes.hcl.entity.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveApplicationRepository extends JpaRepository<LeaveApplication, Long> {
    long countByStatusAndEmployee_Id(LeaveStatus status, Long employeeId);

    long countByStatus(LeaveStatus status);

    @Query("""
            select l from LeaveApplication l
            where (:employeeId is null or l.employee.id = :employeeId)
              and (:email is null or lower(l.employee.email) = lower(:email))
              and (:status is null or l.status = :status)
              and (:fromDate is null or l.endDate >= :fromDate)
              and (:toDate is null or l.startDate <= :toDate)
            order by l.appliedAt desc
            """)
    List<LeaveApplication> search(@Param("employeeId") Long employeeId,
                                  @Param("email") String email,
                                  @Param("status") LeaveStatus status,
                                  @Param("fromDate") LocalDate fromDate,
                                  @Param("toDate") LocalDate toDate);

    @Query("""
            select l from LeaveApplication l
            where l.id = :id and lower(l.employee.email) = lower(:email)
            """)
    Optional<LeaveApplication> findOwned(@Param("id") Long id, @Param("email") String email);

    @Query("select l from LeaveApplication l where l.id = :id")
    Optional<LeaveApplication> findWithEmployee(@Param("id") Long id);
}
