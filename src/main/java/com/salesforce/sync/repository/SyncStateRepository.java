package com.salesforce.sync.repository;

import com.salesforce.sync.model.entity.SyncStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyncStateRepository extends JpaRepository<SyncStateEntity, String> {
    List<SyncStateEntity> findAllByOrderByUpdatedAtDesc();
}
