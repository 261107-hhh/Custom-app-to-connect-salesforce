package com.salesforce.sync.repository;

import com.salesforce.sync.model.entity.LeadEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadRepository extends JpaRepository<LeadEntity, String> {

    @Query("SELECT l FROM LeadEntity l WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(l.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.company) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<LeadEntity> searchLeads(@Param("search") String search, Pageable pageable);

    @Query("SELECT l FROM LeadEntity l WHERE " +
           "(LOWER(l.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(l.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%'))) AND " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(l.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.company) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<LeadEntity> searchLeadsByUser(@Param("userEmail") String userEmail, @Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(l) FROM LeadEntity l WHERE " +
           "(LOWER(l.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(l.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%')))")
    long countByUser(@Param("userEmail") String userEmail);

    @Query("SELECT l FROM LeadEntity l WHERE " +
           "(LOWER(l.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(l.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%')))")
    java.util.List<LeadEntity> findAllByUser(@Param("userEmail") String userEmail);

    @Query("SELECT l FROM LeadEntity l WHERE l.id = :id AND " +
           "(LOWER(l.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(l.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%')))")
    java.util.Optional<LeadEntity> findByIdAndUser(@Param("id") String id, @Param("userEmail") String userEmail);
}
