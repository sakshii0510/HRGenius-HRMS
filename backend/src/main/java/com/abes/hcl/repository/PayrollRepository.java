package com.abes.hcl.repository;

import com.abes.hcl.entity.PayrollRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

public interface PayrollRepository extends JpaRepository<PayrollRecord, Long> {
    @Query("""
            select coalesce(sum(p.netSalary), 0) from PayrollRecord p
            where p.salaryPeriod = :period and (:employeeId is null or p.employee.id = :employeeId)
            """)
    BigDecimal sumNetSalaryForPeriod(@Param("period") LocalDate period, @Param("employeeId") Long employeeId);

    @Query("""
            select p from PayrollRecord p
            where (:employeeId is null or p.employee.id = :employeeId)
              and (:email is null or lower(p.employee.email) = lower(:email))
              and (:fromDate is null or p.salaryPeriod >= :fromDate)
              and (:toDate is null or p.salaryPeriod <= :toDate)
            order by p.salaryPeriod desc, p.id desc
            """)
    List<PayrollRecord> search(@Param("employeeId") Long employeeId,
                               @Param("email") String email,
                               @Param("fromDate") LocalDate fromDate,
                               @Param("toDate") LocalDate toDate);

    @Query("""
            select p from PayrollRecord p
            where p.id = :id and lower(p.employee.email) = lower(:email)
            """)
    Optional<PayrollRecord> findOwned(@Param("id") Long id, @Param("email") String email);
}
