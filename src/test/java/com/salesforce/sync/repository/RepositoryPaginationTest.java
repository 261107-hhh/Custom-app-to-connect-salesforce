package com.salesforce.sync.repository;

import com.salesforce.sync.model.entity.AccountEntity;
import com.salesforce.sync.model.entity.ContactEntity;
import com.salesforce.sync.model.entity.OpportunityEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class RepositoryPaginationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private OpportunityRepository opportunityRepository;

    @Test
    void testSearchContactsPagination() {
        AccountEntity account = new AccountEntity();
        account.setId("acc001");
        account.setName("Acme Test");
        accountRepository.save(account);

        ContactEntity contact = new ContactEntity();
        contact.setId("con001");
        contact.setName("John Doe");
        contact.setAccount(account);
        contactRepository.save(contact);

        Page<ContactEntity> result = contactRepository.searchContacts("", PageRequest.of(0, 10));
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void testSearchOpportunitiesPagination() {
        AccountEntity account = new AccountEntity();
        account.setId("acc002");
        account.setName("Stark Industries");
        accountRepository.save(account);

        OpportunityEntity opp = new OpportunityEntity();
        opp.setId("opp001");
        opp.setName("Cloud Deal");
        opp.setAccount(account);
        opportunityRepository.save(opp);

        Page<OpportunityEntity> result = opportunityRepository.searchOpportunities("", PageRequest.of(0, 10));
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }
}
