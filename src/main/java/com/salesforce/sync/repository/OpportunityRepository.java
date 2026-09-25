package com.salesforce.sync.repository;

import com.salesforce.sync.model.entity.OpportunityEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OpportunityRepository extends JpaRepository<OpportunityEntity, String> {

    @Query("SELECT o FROM OpportunityEntity o LEFT JOIN FETCH o.account WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(o.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.stageName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.account.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<OpportunityEntity> searchOpportunities(@Param("search") String search, Pageable pageable);

    @Query("SELECT o FROM OpportunityEntity o LEFT JOIN FETCH o.account WHERE " +
           "(LOWER(o.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(o.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%'))) AND " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(o.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.stageName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.account.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<OpportunityEntity> searchOpportunitiesByUser(@Param("userEmail") String userEmail, @Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(o) FROM OpportunityEntity o WHERE " +
           "(LOWER(o.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(o.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%')))")
    long countByUser(@Param("userEmail") String userEmail);

    @Query("SELECT o FROM OpportunityEntity o LEFT JOIN FETCH o.account WHERE " +
           "(LOWER(o.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(o.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%')))")
    List<OpportunityEntity> findAllByUser(@Param("userEmail") String userEmail);

    @Query("SELECT o FROM OpportunityEntity o LEFT JOIN FETCH o.account WHERE o.account.id = :accountId ORDER BY o.closeDate DESC")
    List<OpportunityEntity> findByAccountId(@Param("accountId") String accountId);

    @Query("SELECT o FROM OpportunityEntity o LEFT JOIN FETCH o.account WHERE o.account.id = :accountId AND " +
           "(LOWER(o.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(o.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%'))) ORDER BY o.closeDate DESC")
    List<OpportunityEntity> findByAccountIdAndUser(@Param("accountId") String accountId, @Param("userEmail") String userEmail);

    @Query("SELECT o.id as id, o.name as name FROM OpportunityEntity o ORDER BY o.name ASC")
    List<LookupProjection> findAllLookups();

    @Query("SELECT o.id as id, o.name as name FROM OpportunityEntity o WHERE " +
           "(LOWER(o.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(o.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%'))) ORDER BY o.name ASC")
    List<LookupProjection> findAllLookupsByUser(@Param("userEmail") String userEmail);

    @Query("SELECT o FROM OpportunityEntity o LEFT JOIN FETCH o.account WHERE o.id = :id AND " +
           "(LOWER(o.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(o.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%')))")
    java.util.Optional<OpportunityEntity> findByIdAndUser(@Param("id") String id, @Param("userEmail") String userEmail);

    interface LookupProjection {
        String getId();
        String getName();
    }
}
