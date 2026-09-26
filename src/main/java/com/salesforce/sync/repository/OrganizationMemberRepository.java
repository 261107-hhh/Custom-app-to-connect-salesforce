package com.salesforce.sync.repository;

import com.salesforce.sync.model.entity.OrganizationMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMemberEntity, Long> {
    List<OrganizationMemberEntity> findByUserId(Long userId);
    List<OrganizationMemberEntity> findByOrganizationId(String organizationId);
    Optional<OrganizationMemberEntity> findByUserIdAndOrganizationId(Long userId, String organizationId);
    boolean existsByUserIdAndOrganizationId(Long userId, String organizationId);
    long countByOrganizationId(String organizationId);
    void deleteByUserIdAndOrganizationId(Long userId, String organizationId);
}
