package com.abes.hcl.repository;

import com.abes.hcl.entity.WorkspaceModule;
import com.abes.hcl.entity.WorkspaceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkspaceRecordRepository extends JpaRepository<WorkspaceRecord, Long> {
    long countByModuleAndStatusIgnoreCase(WorkspaceModule module, String status);

    long countByModule(WorkspaceModule module);

    boolean existsByModuleAndEmployee_IdAndTitleIgnoreCase(
            WorkspaceModule module, Long employeeId, String title);

    @Query("""
            select r from WorkspaceRecord r left join r.employee e
            where r.module = :module
              and (:employeeId is null or e.id = :employeeId)
              and (:status is null or upper(r.status) = upper(:status))
              and (:search is null or
                   lower(r.title) like lower(concat('%', :search, '%')) or
                   lower(coalesce(r.description, '')) like lower(concat('%', :search, '%')) or
                   lower(coalesce(e.firstName, '')) like lower(concat('%', :search, '%')) or
                   lower(coalesce(e.lastName, '')) like lower(concat('%', :search, '%')))
              and (:ownerOnly = false or
                   (r.visibleToEmployees = true and upper(r.status) in ('PUBLISHED', 'OPEN')) or
                   (:ownerId is not null and e.id = :ownerId))
            order by r.updatedAt desc, r.id desc
            """)
    List<WorkspaceRecord> search(@Param("module") WorkspaceModule module,
                                 @Param("employeeId") Long employeeId,
                                 @Param("status") String status,
                                 @Param("search") String search,
                                 @Param("ownerOnly") boolean ownerOnly,
                                 @Param("ownerId") Long ownerId);

    Optional<WorkspaceRecord> findByIdAndModule(Long id, WorkspaceModule module);

    @Query("""
            select r from WorkspaceRecord r
            where r.id = :id and r.module = :module and r.employee.id = :employeeId
            """)
    Optional<WorkspaceRecord> findOwned(@Param("id") Long id,
                                        @Param("module") WorkspaceModule module,
                                        @Param("employeeId") Long employeeId);
}
