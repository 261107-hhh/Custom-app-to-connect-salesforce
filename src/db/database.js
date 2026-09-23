import { DatabaseSync } from 'node:sqlite';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DATA_DIR = path.resolve(__dirname, '../../data');
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

const DB_PATH = process.env.DB_PATH || path.join(DATA_DIR, 'salesforce_local.db');

export class LocalDatabase {
  constructor(dbPath = DB_PATH) {
    this.db = new DatabaseSync(dbPath);
    this.db.exec('PRAGMA journal_mode = WAL;');
    this.db.exec('PRAGMA foreign_keys = ON;');
    this.initMetaTables();
  }

  initMetaTables() {
    // Sync configuration and credentials storage
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS _sync_config (
        key TEXT PRIMARY KEY,
        value TEXT,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS _sync_history (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        object_name TEXT NOT NULL,
        sync_mode TEXT NOT NULL,
        status TEXT NOT NULL,
        records_fetched INTEGER DEFAULT 0,
        records_upserted INTEGER DEFAULT 0,
        start_time DATETIME DEFAULT CURRENT_TIMESTAMP,
        end_time DATETIME,
        duration_ms INTEGER,
        error_message TEXT
      );

      CREATE TABLE IF NOT EXISTS _sync_state (
        object_name TEXT PRIMARY KEY,
        last_sync_timestamp TEXT,
        last_sync_mode TEXT,
        total_records INTEGER DEFAULT 0,
        last_status TEXT,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
      );
    `);
  }

  // --- Configuration Helpers ---
  getConfig(key) {
    const row = this.db.prepare('SELECT value FROM _sync_config WHERE key = ?').get(key);
    if (!row) return null;
    try {
      return JSON.parse(row.value);
    } catch {
      return row.value;
    }
  }

  setConfig(key, value) {
    const valStr = typeof value === 'object' ? JSON.stringify(value) : String(value);
    const stmt = this.db.prepare(`
      INSERT INTO _sync_config (key, value, updated_at) 
      VALUES (?, ?, CURRENT_TIMESTAMP)
      ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = CURRENT_TIMESTAMP
    `);
    stmt.run(key, valStr);
  }

  getAllConfig() {
    const rows = this.db.prepare('SELECT key, value FROM _sync_config').all();
    const config = {};
    for (const r of rows) {
      try {
        config[r.key] = JSON.parse(r.value);
      } catch {
        config[r.key] = r.value;
      }
    }
    return config;
  }

  // --- Dynamic Salesforce Object Table Management ---
  getTableName(objectName) {
    const sanitized = objectName.toLowerCase().replace(/[^a-z0-9_]/g, '_');
    return `sf_${sanitized}`;
  }

  ensureTableForObject(objectName, sampleRecord = {}) {
    const tableName = this.getTableName(objectName);

    // Initial base table
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS "${tableName}" (
        Id TEXT PRIMARY KEY,
        Name TEXT,
        CreatedDate TEXT,
        LastModifiedDate TEXT,
        SystemModstamp TEXT,
        raw_data TEXT NOT NULL,
        synced_at DATETIME DEFAULT CURRENT_TIMESTAMP
      );
      CREATE INDEX IF NOT EXISTS "idx_${tableName}_lastmod" ON "${tableName}" (LastModifiedDate);
      CREATE INDEX IF NOT EXISTS "idx_${tableName}_sysmod" ON "${tableName}" (SystemModstamp);
    `);

    // Check existing columns
    const columns = this.db.prepare(`PRAGMA table_info("${tableName}")`).all().map(c => c.name);

    // Discover useful string/number scalar fields from sampleRecord and auto-add columns if missing
    for (const [key, val] of Object.entries(sampleRecord)) {
      if (['attributes', 'Id', 'Name', 'CreatedDate', 'LastModifiedDate', 'SystemModstamp', 'raw_data', 'synced_at'].includes(key)) {
        continue;
      }
      if (!columns.includes(key)) {
        let colType = 'TEXT';
        if (typeof val === 'number') {
          colType = Number.isInteger(val) ? 'INTEGER' : 'REAL';
        } else if (typeof val === 'boolean') {
          colType = 'INTEGER';
        }
        try {
          this.db.exec(`ALTER TABLE "${tableName}" ADD COLUMN "${key}" ${colType}`);
          columns.push(key);
        } catch {
          // Ignore if column already exists
        }
      }
    }

    return tableName;
  }

  // Transaction wrapper helper
  transaction(fn) {
    return (...args) => {
      this.db.exec('BEGIN TRANSACTION');
      try {
        const result = fn(...args);
        this.db.exec('COMMIT');
        return result;
      } catch (err) {
        try { this.db.exec('ROLLBACK'); } catch { }
        throw err;
      }
    };
  }

  // --- Upsert Records ---
  upsertRecords(objectName, records) {
    if (!records || records.length === 0) return { inserted: 0, updated: 0 };

    const tableName = this.ensureTableForObject(objectName, records[0]);
    const tableCols = this.db.prepare(`PRAGMA table_info("${tableName}")`).all().map(c => c.name);

    const runUpsert = (recs) => {
      let insertedCount = 0;
      let updatedCount = 0;

      for (const rec of recs) {
        if (!rec.Id) continue;

        const existing = this.db.prepare(`SELECT Id FROM "${tableName}" WHERE Id = ?`).get(rec.Id);

        const rowData = {};
        for (const col of tableCols) {
          if (col === 'raw_data') {
            rowData[col] = JSON.stringify(rec);
          } else if (col === 'synced_at') {
            rowData[col] = new Date().toISOString();
          } else if (rec[col] !== undefined) {
            rowData[col] = typeof rec[col] === 'object' && rec[col] !== null ? JSON.stringify(rec[col]) : rec[col];
          } else {
            rowData[col] = null;
          }
        }

        const colNames = Object.keys(rowData).map(c => `"${c}"`).join(', ');
        const colPlaceholders = Object.keys(rowData).map(() => '?').join(', ');
        const updateSets = Object.keys(rowData)
          .filter(c => c !== 'Id')
          .map(c => `"${c}" = excluded."${c}"`)
          .join(', ');

        const sql = `
          INSERT INTO "${tableName}" (${colNames}) 
          VALUES (${colPlaceholders})
          ON CONFLICT(Id) DO UPDATE SET ${updateSets}
        `;

        this.db.prepare(sql).run(...Object.values(rowData));

        if (existing) {
          updatedCount++;
        } else {
          insertedCount++;
        }
      }

      return { inserted: insertedCount, updated: updatedCount };
    };

    const upsertTx = this.transaction(runUpsert);
    return upsertTx(records);
  }

  // --- Sync State & History Management ---
  startSyncLog(objectName, syncMode) {
    const stmt = this.db.prepare(`
      INSERT INTO _sync_history (object_name, sync_mode, status, start_time)
      VALUES (?, ?, 'RUNNING', CURRENT_TIMESTAMP)
    `);
    const info = stmt.run(objectName, syncMode);
    return info.lastInsertRowid;
  }

  finishSyncLog(syncId, { status, fetched, upserted, durationMs, error = null }) {
    const stmt = this.db.prepare(`
      UPDATE _sync_history
      SET status = ?,
          records_fetched = ?,
          records_upserted = ?,
          end_time = CURRENT_TIMESTAMP,
          duration_ms = ?,
          error_message = ?
      WHERE id = ?
    `);
    stmt.run(status, fetched, upserted, durationMs, error, syncId);
  }

  updateSyncState(objectName, { lastSyncTimestamp, syncMode, status, totalCount }) {
    const stmt = this.db.prepare(`
      INSERT INTO _sync_state (object_name, last_sync_timestamp, last_sync_mode, total_records, last_status, updated_at)
      VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
      ON CONFLICT(object_name) DO UPDATE SET
        last_sync_timestamp = COALESCE(excluded.last_sync_timestamp, _sync_state.last_sync_timestamp),
        last_sync_mode = excluded.last_sync_mode,
        total_records = excluded.total_records,
        last_status = excluded.last_status,
        updated_at = CURRENT_TIMESTAMP
    `);
    stmt.run(objectName, lastSyncTimestamp, syncMode, totalCount, status);
  }

  getSyncState(objectName) {
    return this.db.prepare('SELECT * FROM _sync_state WHERE object_name = ?').get(objectName);
  }

  getAllSyncStates() {
    return this.db.prepare('SELECT * FROM _sync_state ORDER BY updated_at DESC').all();
  }

  resetAllSyncStates() {
    this.db.prepare('DELETE FROM _sync_state').run();
    console.log('[DB] All sync states cleared (new connection detected).');
  }

  getSyncHistory(limit = 50) {
    return this.db.prepare('SELECT * FROM _sync_history ORDER BY id DESC LIMIT ?').all(limit);
  }

  // --- Local Data Querying ---
  getSyncedTables() {
    const rows = this.db.prepare(`
      SELECT name FROM sqlite_master 
      WHERE type='table' AND name LIKE 'sf_%'
      ORDER BY name ASC
    `).all();

    return rows.map(r => {
      const count = this.db.prepare(`SELECT count(*) as count FROM "${r.name}"`).get().count;
      const objectName = r.name.replace(/^sf_/, '');
      return {
        tableName: r.name,
        objectName,
        count
      };
    });
  }

  queryRecords(objectName, { page = 1, limit = 20, search = '', sortBy = 'LastModifiedDate', sortOrder = 'DESC' } = {}) {
    const tableName = this.getTableName(objectName);

    // Check if table exists
    const tableExists = this.db.prepare(`SELECT name FROM sqlite_master WHERE type='table' AND name = ?`).get(tableName);
    if (!tableExists) {
      return { records: [], total: 0, page, limit, totalPages: 0, columns: [] };
    }

    const cols = this.db.prepare(`PRAGMA table_info("${tableName}")`).all();
    const columnNames = cols.map(c => c.name).filter(c => c !== 'raw_data');

    // Safe sort column check
    const validSortCol = cols.some(c => c.name.toLowerCase() === sortBy.toLowerCase()) ? sortBy : 'Id';
    const validOrder = sortOrder.toUpperCase() === 'ASC' ? 'ASC' : 'DESC';

    let whereClause = '';
    const params = [];

    if (search && search.trim() !== '') {
      const term = `%${search.trim()}%`;
      const searchCols = cols.filter(c => c.type === 'TEXT' && c.name !== 'raw_data').map(c => `"${c.name}" LIKE ?`);
      if (searchCols.length > 0) {
        whereClause = `WHERE (${searchCols.join(' OR ')})`;
        for (let i = 0; i < searchCols.length; i++) params.push(term);
      }
    }

    const totalRow = this.db.prepare(`SELECT COUNT(*) as count FROM "${tableName}" ${whereClause}`).get(...params);
    const total = totalRow ? totalRow.count : 0;

    const offset = Math.max(0, (page - 1) * limit);
    const querySql = `
      SELECT * FROM "${tableName}" 
      ${whereClause} 
      ORDER BY "${validSortCol}" ${validOrder} 
      LIMIT ? OFFSET ?
    `;

    const records = this.db.prepare(querySql).all(...params, limit, offset);

    return {
      records,
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
      columns: columnNames
    };
  }

  getRecordById(objectName, id) {
    const tableName = this.getTableName(objectName);
    const row = this.db.prepare(`SELECT * FROM "${tableName}" WHERE Id = ?`).get(id);
    if (row && row.raw_data) {
      try {
        row._parsed_raw_data = JSON.parse(row.raw_data);
      } catch {
        row._parsed_raw_data = null;
      }
    }
    return row;
  }

  getAllRecordsForExport(objectName) {
    const tableName = this.getTableName(objectName);
    return this.db.prepare(`SELECT * FROM "${tableName}" ORDER BY Id ASC`).all();
  }

  close() {
    this.db.close();
  }
}

export const localDb = new LocalDatabase();
export default localDb;
