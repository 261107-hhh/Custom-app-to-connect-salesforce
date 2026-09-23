import localDb from '../db/database.js';
import sfClient from './client.js';

class SyncService {
  constructor() {
    this.currentJob = {
      id: null,
      status: 'idle',
      startedAt: null,
      finishedAt: null,
      objects: [],
      currentObject: null,
      progressPercent: 0,
      totalRecordsSynced: 0,
      details: {},
      logs: [],
      error: null
    };
  }

  log(msg, type = 'info') {
    const entry = {
      timestamp: new Date().toLocaleTimeString(),
      type,
      message: msg
    };
    this.currentJob.logs.push(entry);
    if (this.currentJob.logs.length > 200) {
      this.currentJob.logs.shift();
    }
    console.log(`[SyncService ${entry.timestamp}] [${type.toUpperCase()}] ${msg}`);
  }

  getStatus() {
    return { ...this.currentJob };
  }

  async runSync({ objects = ['Account', 'Contact', 'Opportunity', 'Lead'], mode = 'incremental', filters = {} } = {}) {
    if (this.currentJob.status === 'running') {
      throw new Error('A sync job is already in progress.');
    }

    this.currentJob = {
      id: Date.now().toString(),
      status: 'running',
      startedAt: new Date().toISOString(),
      finishedAt: null,
      objects,
      currentObject: null,
      progressPercent: 0,
      totalRecordsSynced: 0,
      details: {},
      logs: [],
      error: null
    };

    const filterSummary = Object.keys(filters).length > 0 ? ` with filters: ${JSON.stringify(filters)}` : '';
    this.log(`Initiating ${mode.toUpperCase()} sync for objects: ${objects.join(', ')}${filterSummary}`);

    this._executeSync(objects, mode, filters).catch((err) => {
      this.currentJob.status = 'failed';
      this.currentJob.error = err.message;
      this.currentJob.finishedAt = new Date().toISOString();
      this.log(`Sync process failed: ${err.message}`, 'error');
    });

    return { jobId: this.currentJob.id, status: 'started', objects, mode };
  }

  async _executeSync(objects, mode, filters = {}) {
    const totalObjects = objects.length;
    let completedObjects = 0;

    for (const objectName of objects) {
      this.currentJob.currentObject = objectName;
      this.currentJob.progressPercent = Math.round((completedObjects / totalObjects) * 100);
      this.log(`Starting sync for object "${objectName}"...`);

      const syncId = localDb.startSyncLog(objectName, mode);
      const startTime = Date.now();

      try {
        const result = await this.syncSingleObject(objectName, mode, filters);
        const duration = Date.now() - startTime;

        localDb.finishSyncLog(syncId, {
          status: 'SUCCESS',
          fetched: result.fetched,
          upserted: result.upserted,
          durationMs: duration
        });

        this.currentJob.details[objectName] = {
          status: 'success',
          fetched: result.fetched,
          upserted: result.upserted,
          durationMs: duration
        };

        this.currentJob.totalRecordsSynced += result.upserted;
        this.log(`Completed "${objectName}": ${result.fetched} fetched, ${result.upserted} upserted in ${duration}ms`, 'success');
      } catch (err) {
        const duration = Date.now() - startTime;
        localDb.finishSyncLog(syncId, {
          status: 'ERROR',
          fetched: 0,
          upserted: 0,
          durationMs: duration,
          error: err.message
        });

        this.currentJob.details[objectName] = {
          status: 'error',
          error: err.message,
          durationMs: duration
        };

        this.log(`Error syncing "${objectName}": ${err.message}`, 'error');
      }

      completedObjects++;
      this.currentJob.progressPercent = Math.round((completedObjects / totalObjects) * 100);
    }

    this.currentJob.status = 'completed';
    this.currentJob.currentObject = null;
    this.currentJob.finishedAt = new Date().toISOString();
    this.log(`All sync tasks finished. Total records synced: ${this.currentJob.totalRecordsSynced}`);
  }

  async syncSingleObject(objectName, mode = 'incremental', filters = {}) {
    // 1. Introspect schema to discover fields
    let describeInfo;
    try {
      describeInfo = await sfClient.describeObject(objectName);
      this.log(`Describe for "${objectName}" returned ${(describeInfo.fields || []).length} total fields.`);
    } catch (err) {
      throw new Error(`Failed to describe object ${objectName}: ${err.message}`);
    }

    // 2. Build a safe list of queryable fields
    const EXCLUDED_TYPES = new Set([
      'address', 'location', 'base64', 'complexvalue'
    ]);

    const queryableFields = (describeInfo.fields || [])
      .filter(f => {
        if (f.deprecatedAndHidden) return false;
        if (EXCLUDED_TYPES.has(f.type)) return false;
        if (f.accessible === false) return false;
        return true;
      })
      .map(f => f.name);

    this.log(`Selected ${queryableFields.length} queryable fields for "${objectName}".`);

    if (queryableFields.length === 0) {
      queryableFields.push('Id', 'Name', 'SystemModstamp', 'LastModifiedDate', 'CreatedDate');
      this.log(`Warning: No fields from describe, falling back to minimal field set.`, 'error');
    }

    const ensureField = (name) => {
      if (!queryableFields.includes(name)) queryableFields.push(name);
    };
    ensureField('Id');
    ensureField('SystemModstamp');
    ensureField('LastModifiedDate');

    const uniqueFields = [...new Set(queryableFields)];

    // 3. Build SOQL query with WHERE conditions
    let soql = `SELECT ${uniqueFields.join(', ')} FROM ${objectName}`;

    // Collect all WHERE conditions
    const conditions = [];

    // Incremental sync condition
    if (mode === 'incremental') {
      const syncState = localDb.getSyncState(objectName);
      if (syncState && syncState.last_sync_timestamp) {
        const lastSyncIso = new Date(syncState.last_sync_timestamp).toISOString();
        conditions.push(`SystemModstamp > ${lastSyncIso}`);
        this.log(`Incremental filter: SystemModstamp > ${lastSyncIso}`);
      } else {
        this.log(`No previous sync state for "${objectName}". Running initial full fetch.`);
      }
    }

    // User-specified filters
    if (filters.name) {
      // Escape single quotes in SOQL
      const safeName = filters.name.replace(/'/g, "\\'");
      conditions.push(`Name LIKE '%${safeName}%'`);
      this.log(`Filter applied: Name LIKE '%${filters.name}%'`);
    }

    if (filters.createdFrom) {
      const fromDate = new Date(filters.createdFrom).toISOString();
      conditions.push(`CreatedDate >= ${fromDate}`);
      this.log(`Filter applied: CreatedDate >= ${fromDate}`);
    }
    if (filters.createdTo) {
      // Set to end of day
      const toDate = new Date(filters.createdTo + 'T23:59:59.999Z').toISOString();
      conditions.push(`CreatedDate <= ${toDate}`);
      this.log(`Filter applied: CreatedDate <= ${toDate}`);
    }

    if (filters.modifiedFrom) {
      const fromDate = new Date(filters.modifiedFrom).toISOString();
      conditions.push(`LastModifiedDate >= ${fromDate}`);
      this.log(`Filter applied: LastModifiedDate >= ${fromDate}`);
    }
    if (filters.modifiedTo) {
      const toDate = new Date(filters.modifiedTo + 'T23:59:59.999Z').toISOString();
      conditions.push(`LastModifiedDate <= ${toDate}`);
      this.log(`Filter applied: LastModifiedDate <= ${toDate}`);
    }

    // Append WHERE clause if any conditions exist
    if (conditions.length > 0) {
      soql += ` WHERE ${conditions.join(' AND ')}`;
    }

    soql += ` ORDER BY SystemModstamp ASC`;

    // 4. Execute query against Salesforce
    this.log(`SOQL: ${soql.substring(0, 300)}${soql.length > 300 ? '...' : ''}`);

    let queryResult;
    try {
      queryResult = await sfClient.query(soql);
    } catch (queryErr) {
      this.log(`Query with full fields failed: ${queryErr.message}. Retrying with minimal fields...`, 'error');

      const minimalFields = ['Id', 'Name', 'SystemModstamp', 'LastModifiedDate', 'CreatedDate'];
      let fallbackSoql = `SELECT ${minimalFields.join(', ')} FROM ${objectName}`;

      if (conditions.length > 0) {
        fallbackSoql += ` WHERE ${conditions.join(' AND ')}`;
      }
      fallbackSoql += ` ORDER BY SystemModstamp ASC`;

      this.log(`Fallback SOQL: ${fallbackSoql}`);
      queryResult = await sfClient.query(fallbackSoql);
    }

    const records = queryResult.records || [];
    this.log(`Fetched ${records.length} records from Salesforce (totalSize: ${queryResult.totalSize}).`);

    if (records.length === 0) {
      const stats = localDb.queryRecords(objectName, { limit: 1 });
      localDb.updateSyncState(objectName, {
        lastSyncTimestamp: null,
        syncMode: mode,
        status: 'SUCCESS',
        totalCount: stats.total
      });
      return { fetched: 0, upserted: 0, latestModStamp: null };
    }

    // 5. Compute maximum modstamp across all fetched records
    let latestModStamp = null;
    for (const r of records) {
      const stamp = r.SystemModstamp || r.LastModifiedDate;
      if (stamp && (!latestModStamp || new Date(stamp) > new Date(latestModStamp))) {
        latestModStamp = stamp;
      }
    }

    // 6. Batch upsert into SQLite
    const upsertStats = localDb.upsertRecords(objectName, records);

    // 7. Update sync state
    const stats = localDb.queryRecords(objectName, { limit: 1 });
    localDb.updateSyncState(objectName, {
      lastSyncTimestamp: latestModStamp,
      syncMode: mode,
      status: 'SUCCESS',
      totalCount: stats.total
    });

    return {
      fetched: records.length,
      upserted: upsertStats.inserted + upsertStats.updated,
      inserted: upsertStats.inserted,
      updated: upsertStats.updated,
      latestModStamp
    };
  }
}

export const syncService = new SyncService();
export default syncService;
