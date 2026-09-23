#!/usr/bin/env node
import dotenv from 'dotenv';
import localDb from './db/database.js';
import sfClient from './salesforce/client.js';
import syncService from './salesforce/syncService.js';

dotenv.config();

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    objects: ['Account', 'Contact', 'Opportunity', 'Lead'],
    mode: 'incremental'
  };

  for (const arg of args) {
    if (arg.startsWith('--objects=')) {
      options.objects = arg.replace('--objects=', '').split(',').map(s => s.trim());
    } else if (arg.startsWith('--mode=')) {
      options.mode = arg.replace('--mode=', '').toLowerCase();
    } else if (arg === '--full') {
      options.mode = 'full';
    } else if (arg === '--incremental') {
      options.mode = 'incremental';
    } else if (arg === '--help' || arg === '-h') {
      console.log(`
Salesforce Local Database Sync CLI

Usage:
  node src/cli.js [options]

Options:
  --objects=<names>   Comma-separated list of objects to sync (default: Account,Contact,Opportunity,Lead)
  --mode=<mode>       Sync mode: 'full' or 'incremental' (default: incremental)
  --full              Shorthand for --mode=full
  --incremental       Shorthand for --mode=incremental
  --help, -h          Show this help message
      `);
      process.exit(0);
    }
  }

  return options;
}

async function main() {
  console.log('--- Salesforce Local Sync CLI ---');
  const { objects, mode } = parseArgs();

  // Check saved connection or environment variables
  const status = sfClient.getStatus();
  if (!status.connected) {
    if (process.env.SF_USERNAME && process.env.SF_PASSWORD) {
      console.log('Connecting using environment credentials...');
      await sfClient.connect({
        mode: 'password',
        loginUrl: process.env.SF_LOGIN_URL || 'https://login.salesforce.com',
        username: process.env.SF_USERNAME,
        password: process.env.SF_PASSWORD,
        securityToken: process.env.SF_SECURITY_TOKEN || ''
      });
    } else {
      console.log('No live credentials in .env, falling back to mock mode...');
      await sfClient.connect({ mode: 'mock' });
    }
  }

  console.log(`Running ${mode.toUpperCase()} sync for objects: ${objects.join(', ')}...`);

  for (const obj of objects) {
    const syncId = localDb.startSyncLog(obj, mode);
    const start = Date.now();
    try {
      console.log(`Syncing ${obj}...`);
      const result = await syncService.syncSingleObject(obj, mode);
      const duration = Date.now() - start;

      localDb.finishSyncLog(syncId, {
        status: 'SUCCESS',
        fetched: result.fetched,
        upserted: result.upserted,
        durationMs: duration
      });

      const stats = localDb.queryRecords(obj, { limit: 1 });
      localDb.updateSyncState(obj, {
        lastSyncTimestamp: result.latestModStamp || new Date().toISOString(),
        syncMode: mode,
        status: 'SUCCESS',
        totalCount: stats.total
      });

      console.log(`✓ ${obj}: ${result.fetched} fetched, ${result.upserted} upserted in ${duration}ms (Total in local DB: ${stats.total})`);
    } catch (err) {
      const duration = Date.now() - start;
      localDb.finishSyncLog(syncId, {
        status: 'ERROR',
        fetched: 0,
        upserted: 0,
        durationMs: duration,
        error: err.message
      });
      console.error(`✗ ${obj} failed: ${err.message}`);
    }
  }

  console.log('--- Sync Completed ---');
  process.exit(0);
}

main().catch(err => {
  console.error('Fatal CLI Error:', err);
  process.exit(1);
});
