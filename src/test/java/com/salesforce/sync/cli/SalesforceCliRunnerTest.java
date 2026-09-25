package com.salesforce.sync.cli;

import com.salesforce.sync.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class SalesforceCliRunnerTest {

    @Autowired
    private SalesforceCliRunner cliRunner;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void testCliSyncExecution() throws Exception {
        cliRunner.run("--sync", "--objects=Account", "--incremental");
        assertTrue(accountRepository.count() > 0, "CLI sync should populate records in the repository");
    }

    @Test
    void testCliHelp() throws Exception {
        cliRunner.run("--help");
        // Should not throw exception
    }
}
