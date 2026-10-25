
package com.abes.hcl.repository;

import com.abes.hcl.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface EmployeeRepository
        extends JpaRepository<Employee, Long> {
    Optional<Employee> findByEmailIgnoreCase(String email);

    @Query("""
            select e from Employee e
            where (:search is null or
                   lower(concat(e.firstName, ' ', e.lastName)) like lower(concat('%', :search, '%')) or
                   lower(e.email) like lower(concat('%', :search, '%')) or
                   lower(coalesce(e.department, '')) like lower(concat('%', :search, '%')) or
                   lower(coalesce(e.designation, '')) like lower(concat('%', :search, '%')))
              and (:department is null or lower(coalesce(e.department, '')) = lower(:department))
              and (:designation is null or lower(coalesce(e.designation, '')) = lower(:designation))
            order by e.lastName, e.firstName
            """)
    List<Employee> search(@Param("search") String search,
                          @Param("department") String department,
                          @Param("designation") String designation);

    @Query("""
            select e from Employee e
            where lower(e.email) = lower(:email)
              and (:search is null or
                   lower(concat(e.firstName, ' ', e.lastName)) like lower(concat('%', :search, '%')) or
                   lower(e.email) like lower(concat('%', :search, '%')) or
                   lower(coalesce(e.department, '')) like lower(concat('%', :search, '%')) or
                   lower(coalesce(e.designation, '')) like lower(concat('%', :search, '%')))
              and (:department is null or lower(coalesce(e.department, '')) = lower(:department))
              and (:designation is null or lower(coalesce(e.designation, '')) = lower(:designation))
            """)
    List<Employee> searchOwn(@Param("email") String email,
                             @Param("search") String search,
                             @Param("department") String department,
                             @Param("designation") String designation);
}
