package com.salesforce.sync.repository;

import com.salesforce.sync.model.entity.AccountEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, String> {

    @Query("SELECT a FROM AccountEntity a WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.industry) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.billingCity) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<AccountEntity> searchAccounts(@Param("search") String search, Pageable pageable);

    @Query("SELECT a FROM AccountEntity a WHERE " +
           "(LOWER(a.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(a.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%'))) AND " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.industry) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.billingCity) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<AccountEntity> searchAccountsByUser(@Param("userEmail") String userEmail, @Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(a) FROM AccountEntity a WHERE " +
           "(LOWER(a.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(a.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%')))")
    long countByUser(@Param("userEmail") String userEmail);

    @Query("SELECT a FROM AccountEntity a WHERE " +
           "(LOWER(a.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(a.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%')))")
    List<AccountEntity> findAllByUser(@Param("userEmail") String userEmail);

    @Query("SELECT a.id as id, a.name as name FROM AccountEntity a ORDER BY a.name ASC")
    List<LookupProjection> findAllLookups();

    @Query("SELECT a.id as id, a.name as name FROM AccountEntity a WHERE " +
           "(LOWER(a.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(a.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%'))) ORDER BY a.name ASC")
    List<LookupProjection> findAllLookupsByUser(@Param("userEmail") String userEmail);

    @Query("SELECT a FROM AccountEntity a WHERE a.id = :id AND " +
           "(LOWER(a.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(a.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%')))")
    java.util.Optional<AccountEntity> findByIdAndUser(@Param("id") String id, @Param("userEmail") String userEmail);

    interface LookupProjection {
        String getId();
        String getName();
    }
}
