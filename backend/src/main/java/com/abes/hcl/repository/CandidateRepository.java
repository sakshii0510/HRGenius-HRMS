package com.abes.hcl.repository;

import com.abes.hcl.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    Optional<Candidate> findByEmailIgnoreCase(String email);

    @Query("""
            select c from Candidate c
            where (:search is null or
                   lower(c.name) like lower(concat('%', :search, '%')) or
                   lower(c.email) like lower(concat('%', :search, '%')))
            order by c.name
            """)
    List<Candidate> search(@Param("search") String search);
}
