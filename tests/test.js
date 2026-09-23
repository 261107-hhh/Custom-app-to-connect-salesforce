import assert from 'assert';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

import { LocalDatabase } from '../src/db/database.js';
import sfClient from '../src/salesforce/client.js';
import syncService from '../src/salesforce/syncService.js';
import mockSalesforce from '../src/mock/mockSalesforce.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function runTests() {
  console.log('==================================================');
  console.log('   Running Salesforce Local Sync Test Suite       ');
  console.log('==================================================\n');

  const testDbPath = path.join(__dirname, 'test_salesforce.db');
  if (fs.existsSync(testDbPath)) {
    try { fs.unlinkSync(testDbPath); } catch {}
  }

  const db = new LocalDatabase(testDbPath);

  try {
    // 1. Database Schema & Config Test
    console.log('[Test 1] Testing Database Config & Tables Initialization...');
    db.setConfig('test_key', { foo: 'bar', num: 42 });
    const configVal = db.getConfig('test_key');
    assert.strictEqual(configVal.foo, 'bar');
    assert.strictEqual(configVal.num, 42);
    console.log('✓ Config store & JSON serialization working.');

    // 2. Dynamic Table Creation & Upsert Test
    console.log('\n[Test 2] Testing Dynamic Table Creation & Record Upsert...');
    const sampleAccounts = [
      {
        Id: '001xx0000000001AAA',
        Name: 'Test Corp Alpha',
        Industry: 'Technology',
        AnnualRevenue: 1000000,
        CreatedDate: '2026-01-01T00:00:00.000Z',
        LastModifiedDate: '2026-01-01T00:00:00.000Z',
        SystemModstamp: '2026-01-01T00:00:00.000Z'
      },
      {
        Id: '001xx0000000002AAA',
        Name: 'Test Corp Beta',
        Industry: 'Finance',
        AnnualRevenue: 2500000,
        CreatedDate: '2026-01-02T00:00:00.000Z',
        LastModifiedDate: '2026-01-02T00:00:00.000Z',
        SystemModstamp: '2026-01-02T00:00:00.000Z'
      }
    ];

    const upsertRes1 = db.upsertRecords('Account', sampleAccounts);
    assert.strictEqual(upsertRes1.inserted, 2);
    assert.strictEqual(upsertRes1.updated, 0);

    const query1 = db.queryRecords('Account', { sortBy: 'LastModifiedDate', sortOrder: 'ASC' });
    assert.strictEqual(query1.total, 2);
    assert.strictEqual(query1.records[0].Name, 'Test Corp Alpha');
    assert.strictEqual(query1.records[1].Name, 'Test Corp Beta');
    assert(query1.columns.includes('Industry'));
    assert(query1.columns.includes('AnnualRevenue'));
    console.log('✓ Initial insert and dynamic column generation verified.');

    // Test Upsert Update behavior on Id conflict
    const updatedAccount = [
      {
        Id: '001xx0000000001AAA',
        Name: 'Test Corp Alpha - RENAMED',
        Industry: 'Technology',
        AnnualRevenue: 1200000,
        CreatedDate: '2026-01-01T00:00:00.000Z',
        LastModifiedDate: '2026-02-01T00:00:00.000Z',
        SystemModstamp: '2026-02-01T00:00:00.000Z'
      }
    ];

    const upsertRes2 = db.upsertRecords('Account', updatedAccount);
    assert.strictEqual(upsertRes2.inserted, 0);
    assert.strictEqual(upsertRes2.updated, 1);

    const recordAlpha = db.getRecordById('Account', '001xx0000000001AAA');
    assert.strictEqual(recordAlpha.Name, 'Test Corp Alpha - RENAMED');
    assert.strictEqual(recordAlpha.AnnualRevenue, 1200000);
    assert.strictEqual(recordAlpha._parsed_raw_data.Name, 'Test Corp Alpha - RENAMED');
    console.log('✓ Upsert correctly updated record on Id conflict.');

    // 3. Search & Pagination Test
    console.log('\n[Test 3] Testing Search & Pagination...');
    const searchRes = db.queryRecords('Account', { search: 'Beta' });
    assert.strictEqual(searchRes.total, 1);
    assert.strictEqual(searchRes.records[0].Name, 'Test Corp Beta');

    const searchNone = db.queryRecords('Account', { search: 'NonExistent' });
    assert.strictEqual(searchNone.total, 0);
    console.log('✓ Search filter working accurately.');

    // 4. Mock Salesforce Connection Test
    console.log('\n[Test 4] Testing Salesforce Client Mock Connection...');
    const connResult = await sfClient.connect({ mode: 'mock' });
    assert.strictEqual(connResult.success, true);
    assert.strictEqual(connResult.mode, 'mock');

    const globalDesc = await sfClient.describeGlobal();
    assert(globalDesc.sobjects.length > 0);
    const hasAccount = globalDesc.sobjects.some(o => o.name === 'Account');
    assert(hasAccount, 'Global describe should contain Account');
    console.log(`✓ Salesforce client connected in mock mode. Found ${globalDesc.sobjects.length} queryable sObjects.`);

    // 5. Full Sync Pipeline Execution Test
    console.log('\n[Test 5] Testing End-to-End Sync Pipeline Execution...');
    const syncResult = await syncService.syncSingleObject('Contact', 'full');
    assert(syncResult.fetched > 0, 'Should fetch contact records from mock');
    assert.strictEqual(syncResult.fetched, syncResult.upserted);
    console.log(`✓ Contact sync completed: ${syncResult.fetched} records fetched and upserted.`);

    // 6. Incremental Sync Change Detection Test
    console.log('\n[Test 6] Testing Incremental Sync Delta Detection...');
    // Initial full sync for Opportunity
    const oppSync1 = await syncService.syncSingleObject('Opportunity', 'full');
    console.log(`  Initial Opportunity sync fetched ${oppSync1.fetched} records.`);

    // Immediately run incremental sync - should fetch 0 since nothing changed
    const oppSyncIncremental1 = await syncService.syncSingleObject('Opportunity', 'incremental');
    console.log(`  Immediate incremental sync fetched ${oppSyncIncremental1.fetched} records (Expected 0).`);
    assert.strictEqual(oppSyncIncremental1.fetched, 0);

    // Simulate adding a new record in Mock Salesforce
    mockSalesforce.simulateNewOrModifiedRecord('Opportunity');
    console.log('  Simulated 1 new Opportunity record created in Salesforce.');

    // Run incremental sync again - should now fetch exactly the 1 new record
    const oppSyncIncremental2 = await syncService.syncSingleObject('Opportunity', 'incremental');
    console.log(`  Subsequent incremental sync fetched ${oppSyncIncremental2.fetched} records (Expected 1).`);
    assert.strictEqual(oppSyncIncremental2.fetched, 1);
    console.log('✓ Incremental sync correctly detected and pulled only changed delta records!');

    console.log('\n==================================================');
    console.log('  ALL TESTS PASSED SUCCESSFULLY!                 ');
    console.log('==================================================');
  } finally {
    try { db.close(); } catch {}
    if (fs.existsSync(testDbPath)) {
      try { fs.unlinkSync(testDbPath); } catch {}
    }
  }
}

runTests().catch(err => {
  console.error('\n✗ Test failed with error:', err);
  process.exit(1);
});
