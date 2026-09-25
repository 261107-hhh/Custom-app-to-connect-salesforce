package com.salesforce.sync.repository;

import com.salesforce.sync.model.entity.SyncHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyncHistoryRepository extends JpaRepository<SyncHistoryEntity, Long> {
    List<SyncHistoryEntity> findTop50ByOrderByIdDesc();
    List<SyncHistoryEntity> findTop50ByUserEmailOrderByIdDesc(String userEmail);
}
