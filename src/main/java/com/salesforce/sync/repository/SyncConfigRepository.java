package com.salesforce.sync.repository;

import com.salesforce.sync.model.entity.SyncConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncConfigRepository extends JpaRepository<SyncConfigEntity, String> {
}
