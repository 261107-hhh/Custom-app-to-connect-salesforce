package com.salesforce.sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
public class SchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void runMigration() {
        System.out.println("=== RUNNING SCHEMA MIGRATION FOR MULTI-TENANCY ===");
        try {
            jdbcTemplate.execute("ALTER TABLE sf_account ADD COLUMN IF NOT EXISTS organization_id VARCHAR(64)");
            jdbcTemplate.execute("ALTER TABLE sf_contact ADD COLUMN IF NOT EXISTS organization_id VARCHAR(64)");
            jdbcTemplate.execute("ALTER TABLE sf_opportunity ADD COLUMN IF NOT EXISTS organization_id VARCHAR(64)");
            jdbcTemplate.execute("ALTER TABLE sf_lead ADD COLUMN IF NOT EXISTS organization_id VARCHAR(64)");

            jdbcTemplate.execute("UPDATE sf_account SET organization_id = 'default_org' WHERE organization_id IS NULL");
            jdbcTemplate.execute("UPDATE sf_contact SET organization_id = 'default_org' WHERE organization_id IS NULL");
            jdbcTemplate.execute("UPDATE sf_opportunity SET organization_id = 'default_org' WHERE organization_id IS NULL");
            jdbcTemplate.execute("UPDATE sf_lead SET organization_id = 'default_org' WHERE organization_id IS NULL");

            System.out.println("=== SCHEMA MIGRATION COMPLETED SUCCESSFULLY ===");
        } catch (Exception e) {
            System.err.println("Migration error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
