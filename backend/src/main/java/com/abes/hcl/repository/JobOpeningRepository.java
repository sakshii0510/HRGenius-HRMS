package com.abes.hcl.repository;

import com.abes.hcl.entity.JobOpening;
import com.abes.hcl.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobOpeningRepository extends JpaRepository<JobOpening, Long> {
    long countByStatus(JobStatus status);

    @Query("""
            select j from JobOpening j
            where (:status is null or j.status = :status)
              and (:search is null or
                   lower(j.title) like lower(concat('%', :search, '%')) or
                   lower(j.department) like lower(concat('%', :search, '%')) or
                   lower(j.location) like lower(concat('%', :search, '%')))
            order by j.postedDate desc, j.id desc
            """)
    List<JobOpening> search(@Param("status") JobStatus status, @Param("search") String search);
}
