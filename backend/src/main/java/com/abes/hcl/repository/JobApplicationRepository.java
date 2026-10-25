package com.abes.hcl.repository;

import com.abes.hcl.entity.ApplicationStatus;
import com.abes.hcl.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    boolean existsByJobOpeningIdAndCandidateEmailIgnoreCase(Long jobOpeningId, String candidateEmail);

    @Query("""
            select a from JobApplication a
            where (:jobOpeningId is null or a.jobOpening.id = :jobOpeningId)
              and (:status is null or a.status = :status)
              and (:email is null or lower(a.candidate.email) = lower(:email))
              and (:search is null or
                   lower(a.candidate.name) like lower(concat('%', :search, '%')) or
                   lower(a.candidate.email) like lower(concat('%', :search, '%')))
            order by a.appliedAt desc
            """)
    List<JobApplication> search(@Param("jobOpeningId") Long jobOpeningId,
                                @Param("status") ApplicationStatus status,
                                @Param("email") String email,
                                @Param("search") String search);
}
