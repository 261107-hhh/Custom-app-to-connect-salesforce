package com.salesforce.sync.repository;

import com.salesforce.sync.model.entity.ContactEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactRepository extends JpaRepository<ContactEntity, String> {

    @Query("SELECT c FROM ContactEntity c LEFT JOIN FETCH c.account WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.account.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ContactEntity> searchContacts(@Param("search") String search, Pageable pageable);

    @Query("SELECT c FROM ContactEntity c LEFT JOIN FETCH c.account WHERE " +
           "(LOWER(c.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(c.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%'))) AND " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.account.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ContactEntity> searchContactsByUser(@Param("userEmail") String userEmail, @Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(c) FROM ContactEntity c WHERE " +
           "(LOWER(c.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(c.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%')))")
    long countByUser(@Param("userEmail") String userEmail);

    @Query("SELECT c FROM ContactEntity c LEFT JOIN FETCH c.account WHERE " +
           "(LOWER(c.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(c.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%')))")
    List<ContactEntity> findAllByUser(@Param("userEmail") String userEmail);

    @Query("SELECT c FROM ContactEntity c LEFT JOIN FETCH c.account WHERE c.account.id = :accountId ORDER BY c.name ASC")
    List<ContactEntity> findByAccountId(@Param("accountId") String accountId);

    @Query("SELECT c FROM ContactEntity c LEFT JOIN FETCH c.account WHERE c.account.id = :accountId AND " +
           "(LOWER(c.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(c.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%'))) ORDER BY c.name ASC")
    List<ContactEntity> findByAccountIdAndUser(@Param("accountId") String accountId, @Param("userEmail") String userEmail);

    @Query("SELECT c.id as id, c.name as name FROM ContactEntity c ORDER BY c.name ASC")
    List<LookupProjection> findAllLookups();

    @Query("SELECT c.id as id, c.name as name FROM ContactEntity c WHERE " +
           "(LOWER(c.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(c.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%'))) ORDER BY c.name ASC")
    List<LookupProjection> findAllLookupsByUser(@Param("userEmail") String userEmail);

    @Query("SELECT c FROM ContactEntity c LEFT JOIN FETCH c.account WHERE c.id = :id AND " +
           "(LOWER(c.customAppCreatedBy) = LOWER(:userEmail) OR LOWER(c.syncedBy) LIKE LOWER(CONCAT('%,', :userEmail, ',%')))")
    java.util.Optional<ContactEntity> findByIdAndUser(@Param("id") String id, @Param("userEmail") String userEmail);

    interface LookupProjection {
        String getId();
        String getName();
    }
}
