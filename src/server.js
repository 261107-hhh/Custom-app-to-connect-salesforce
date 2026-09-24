import express from 'express';
import cors from 'cors';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';

import localDb from './db/database.js';
import sfClient from './salesforce/client.js';
import syncService from './salesforce/syncService.js';
import mockSalesforce from './mock/mockSalesforce.js';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, '../public')));

// --- AUTH & CONNECTION ENDPOINTS ---

app.get('/api/auth/status', (req, res) => {
  try {
    const status = sfClient.getStatus();
    res.json({ success: true, data: status });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

app.post('/api/auth/connect', async (req, res) => {
  try {
    const credentials = req.body;
    const result = await sfClient.connect(credentials);
    res.json({ success: true, data: result });
  } catch (err) {
    res.status(400).json({ success: false, error: err.message });
  }
});

app.post('/api/auth/disconnect', async (req, res) => {
  try {
    const result = await sfClient.disconnect();
    res.json({ success: true, data: result });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// --- METADATA & OBJECTS ENDPOINTS ---

app.get('/api/objects', async (req, res) => {
  try {
    const globalDescribe = await sfClient.describeGlobal();
    const sobjects = (globalDescribe.sobjects || [])
      .filter(o => o.queryable)
      .map(o => ({
        name: o.name,
        label: o.label,
        custom: o.custom,
        keyPrefix: o.keyPrefix
      }));

    res.json({ success: true, data: sobjects });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

app.get('/api/objects/:name/describe', async (req, res) => {
  try {
    const desc = await sfClient.describeObject(req.params.name);
    res.json({ success: true, data: desc });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// --- SYNC ORCHESTRATION ENDPOINTS ---

app.post('/api/sync/run', async (req, res) => {
  try {
    const { objects = ['Account', 'Contact', 'Opportunity', 'Lead'], mode = 'incremental', filters = {} } = req.body;
    const result = await syncService.runSync({ objects, mode, filters });
    res.json({ success: true, data: result });
  } catch (err) {
    res.status(400).json({ success: false, error: err.message });
  }
});

app.get('/api/sync/status', (req, res) => {
  res.json({ success: true, data: syncService.getStatus() });
});

app.get('/api/sync/history', (req, res) => {
  try {
    const limit = parseInt(req.query.limit, 10) || 50;
    const history = localDb.getSyncHistory(limit);
    res.json({ success: true, data: history });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

app.get('/api/sync/states', (req, res) => {
  try {
    const states = localDb.getAllSyncStates();
    res.json({ success: true, data: states });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// --- LOCAL DATA EXPLORER ENDPOINTS ---

app.get('/api/data/tables', (req, res) => {
  try {
    const tables = localDb.getSyncedTables();
    res.json({ success: true, data: tables });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

app.get('/api/data/:objectName', (req, res) => {
  try {
    const { objectName } = req.params;
    const page = parseInt(req.query.page, 10) || 1;
    const limit = parseInt(req.query.limit, 10) || 20;
    const search = req.query.search || '';
    const sortBy = req.query.sortBy || 'LastModifiedDate';
    const sortOrder = req.query.sortOrder || 'DESC';

    const result = localDb.queryRecords(objectName, { page, limit, search, sortBy, sortOrder });
    res.json({ success: true, data: result });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

app.get('/api/data/:objectName/:id', (req, res) => {
  try {
    const { objectName, id } = req.params;
    const record = localDb.getRecordById(objectName, id);
    if (!record) {
      return res.status(404).json({ success: false, error: 'Record not found' });
    }
    res.json({ success: true, data: record });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// --- RELATIONAL & LOOKUP ENDPOINTS ---

app.get('/api/lookups/:objectName', (req, res) => {
  try {
    const { objectName } = req.params;
    const options = localDb.getLookupOptions(objectName);
    res.json({ success: true, data: options });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

app.get('/api/data/:objectName/:id/related', (req, res) => {
  try {
    const { objectName, id } = req.params;
    const related = localDb.getRelatedRecords(objectName, id);
    if (!related) {
      return res.status(404).json({ success: false, error: 'Record not found' });
    }
    res.json({ success: true, data: related });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// --- CREATE RECORD ENDPOINTS ---

app.get('/api/objects/:name/fields', async (req, res) => {
  try {
    const fields = await sfClient.getCreatableFields(req.params.name);
    res.json({ success: true, data: fields });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

app.post('/api/data/:objectName/create', async (req, res) => {
  try {
    const { objectName } = req.params;
    const recordData = req.body;

    if (!recordData || Object.keys(recordData).length === 0) {
      return res.status(400).json({ success: false, error: 'No field data provided.' });
    }

    // 1. Push to Salesforce
    const createResult = await sfClient.createRecord(objectName, recordData);

    // 2. Fetch the full record back from Salesforce to store locally
    let fullRecord = { Id: createResult.id, ...recordData };
    try {
      const queryResult = await sfClient.query(
        `SELECT Id, Name, SystemModstamp, LastModifiedDate, CreatedDate FROM ${objectName} WHERE Id = '${createResult.id}'`
      );
      if (queryResult.records && queryResult.records.length > 0) {
        fullRecord = { ...queryResult.records[0], ...recordData, Id: createResult.id };
      }
    } catch (fetchErr) {
      console.warn(`[Create] Could not fetch back full record: ${fetchErr.message}. Storing partial.`);
    }

    // 3. Store in local SQLite
    try {
      localDb.upsertRecords(objectName, [fullRecord]);
    } catch (dbErr) {
      console.warn(`[Create] Could not store record locally: ${dbErr.message}`);
    }

    res.json({
      success: true,
      data: {
        id: createResult.id,
        objectName,
        message: `${objectName} record created successfully in Salesforce!`
      }
    });
  } catch (err) {
    res.status(400).json({ success: false, error: err.message });
  }
});

// --- EXPORT DATA ENDPOINT ---

app.get('/api/export/:objectName', (req, res) => {
  try {
    const { objectName } = req.params;
    const format = (req.query.format || 'json').toLowerCase();
    const records = localDb.getAllRecordsForExport(objectName);

    if (format === 'csv') {
      if (records.length === 0) {
        res.setHeader('Content-Type', 'text/csv');
        res.setHeader('Content-Disposition', `attachment; filename="${objectName}_export.csv"`);
        return res.send('Id\n');
      }

      // Extract keys excluding raw_data for clean CSV
      const keys = Object.keys(records[0]).filter(k => k !== 'raw_data');
      const csvRows = [keys.join(',')];

      for (const row of records) {
        const values = keys.map(k => {
          const val = row[k] === null || row[k] === undefined ? '' : String(row[k]);
          return `"${val.replace(/"/g, '""')}"`;
        });
        csvRows.push(values.join(','));
      }

      res.setHeader('Content-Type', 'text/csv');
      res.setHeader('Content-Disposition', `attachment; filename="${objectName}_export.csv"`);
      return res.send(csvRows.join('\n'));
    }

    // Default JSON export
    const sanitized = records.map(r => {
      try {
        return JSON.parse(r.raw_data);
      } catch {
        return r;
      }
    });

    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Content-Disposition', `attachment; filename="${objectName}_export.json"`);
    res.json(sanitized);
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// --- MOCK TESTING HELPERS ---

app.post('/api/mock/simulate-update', (req, res) => {
  try {
    const { objectName = 'Account' } = req.body;
    const newRecord = mockSalesforce.simulateNewOrModifiedRecord(objectName);
    res.json({
      success: true,
      message: `Simulated a new/updated ${objectName} in mock Salesforce. You can now run incremental sync!`,
      record: newRecord
    });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// Start Server
app.listen(PORT, () => {
  console.log(`=======================================================`);
  console.log(`  Salesforce Local Sync Server running on port ${PORT} `);
  console.log(`  Web Dashboard: http://localhost:${PORT}              `);
  console.log(`=======================================================`);
});

export default app;
