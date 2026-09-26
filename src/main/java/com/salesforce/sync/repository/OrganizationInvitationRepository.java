package com.salesforce.sync.repository;

import com.salesforce.sync.model.entity.OrganizationInvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationInvitationRepository extends JpaRepository<OrganizationInvitationEntity, Long> {
    Optional<OrganizationInvitationEntity> findByToken(String token);
    List<OrganizationInvitationEntity> findByOrganizationId(String organizationId);
    List<OrganizationInvitationEntity> findByOrganizationIdAndStatus(String organizationId, String status);
    Optional<OrganizationInvitationEntity> findByOrganizationIdAndEmailAndStatus(String organizationId, String email, String status);
}
