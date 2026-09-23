// Salesforce Local Sync Frontend Controller

const state = {
  activeTab: 'tabSync',
  connection: null,
  syncPollingInterval: null,
  activeExplorerTable: 'account',
  explorerPage: 1,
  explorerLimit: 15,
  explorerSearch: '',
  explorerSortBy: 'LastModifiedDate',
  explorerSortOrder: 'DESC',
  availableTables: []
};

// --- DOM ELEMENTS ---
const el = {
  // Navigation
  tabs: document.querySelectorAll('.nav-tab'),
  panels: document.querySelectorAll('.tab-panel'),
  headerDbRecords: document.getElementById('headerDbRecords'),
  headerDbTables: document.getElementById('headerDbTables'),
  connectionStatusPill: document.getElementById('connectionStatusPill'),
  connectionStatusText: document.getElementById('connectionStatusText'),
  syncActiveEnvTag: document.getElementById('syncActiveEnvTag'),

  // Sync Center
  btnStartSync: document.getElementById('btnStartSync'),
  syncModeIncremental: document.getElementById('syncModeIncremental'),
  syncModeFull: document.getElementById('syncModeFull'),
  objectCheckboxes: document.querySelectorAll('.obj-checkbox'),
  btnSelectAllObjects: document.getElementById('btnSelectAllObjects'),
  btnDeselectAllObjects: document.getElementById('btnDeselectAllObjects'),
  btnSimulateUpdate: document.getElementById('btnSimulateUpdate'),
  mockSimulatorBox: document.getElementById('mockSimulatorBox'),
  // Filters
  btnToggleFilters: document.getElementById('btnToggleFilters'),
  filterPanel: document.getElementById('filterPanel'),
  filterName: document.getElementById('filterName'),
  filterCreatedFrom: document.getElementById('filterCreatedFrom'),
  filterCreatedTo: document.getElementById('filterCreatedTo'),
  filterModifiedFrom: document.getElementById('filterModifiedFrom'),
  filterModifiedTo: document.getElementById('filterModifiedTo'),
  btnClearFilters: document.getElementById('btnClearFilters'),
  jobStatusBadge: document.getElementById('jobStatusBadge'),
  currentTaskLabel: document.getElementById('currentTaskLabel'),
  progressPercentLabel: document.getElementById('progressPercentLabel'),
  progressBarFill: document.getElementById('progressBarFill'),
  statFetched: document.getElementById('statFetched'),
  statUpserted: document.getElementById('statUpserted'),
  statObjectsDone: document.getElementById('statObjectsDone'),
  syncTerminalBody: document.getElementById('syncTerminalBody'),
  btnClearLogs: document.getElementById('btnClearLogs'),
  objectSummaryCards: document.getElementById('objectSummaryCards'),

  // Data Explorer
  tableSwitcher: document.getElementById('tableSwitcher'),
  searchInput: document.getElementById('searchInput'),
  localDataTableHead: document.getElementById('localDataTableHead'),
  localDataTableBody: document.getElementById('localDataTableBody'),
  paginationInfo: document.getElementById('paginationInfo'),
  pageIndicator: document.getElementById('pageIndicator'),
  btnPrevPage: document.getElementById('btnPrevPage'),
  btnNextPage: document.getElementById('btnNextPage'),
  btnExportCsv: document.getElementById('btnExportCsv'),
  btnExportJson: document.getElementById('btnExportJson'),
  btnRefreshData: document.getElementById('btnRefreshData'),

  // History
  syncHistoryTableBody: document.getElementById('syncHistoryTableBody'),
  btnRefreshHistory: document.getElementById('btnRefreshHistory'),

  // Connection Form
  authModeBtns: document.querySelectorAll('.auth-mode-btn'),
  fieldsEca: document.getElementById('fieldsEca'),
  fieldsMock: document.getElementById('fieldsMock'),
  fieldsPassword: document.getElementById('fieldsPassword'),
  ecaInstanceUrl: document.getElementById('ecaInstanceUrl'),
  ecaClientId: document.getElementById('ecaClientId'),
  ecaClientSecret: document.getElementById('ecaClientSecret'),
  sfLoginUrl: document.getElementById('sfLoginUrl'),
  sfUsername: document.getElementById('sfUsername'),
  sfPassword: document.getElementById('sfPassword'),
  sfSecurityToken: document.getElementById('sfSecurityToken'),
  btnConnectSalesforce: document.getElementById('btnConnectSalesforce'),
  btnDisconnectSalesforce: document.getElementById('btnDisconnectSalesforce'),
  connectionFeedback: document.getElementById('connectionFeedback'),

  // Modal
  recordModal: document.getElementById('recordModal'),
  modalRecordTitle: document.getElementById('modalRecordTitle'),
  modalRecordId: document.getElementById('modalRecordId'),
  modalJsonDisplay: document.getElementById('modalJsonDisplay'),
  btnCloseModal: document.getElementById('btnCloseModal'),
  btnCopyJson: document.getElementById('btnCopyJson'),

  // Create Record
  btnCreateRecord: document.getElementById('btnCreateRecord'),
  createRecordModal: document.getElementById('createRecordModal'),
  btnCloseCreateModal: document.getElementById('btnCloseCreateModal'),
  createObjectSelector: document.getElementById('createObjectSelector'),
  createFieldsLoading: document.getElementById('createFieldsLoading'),
  createFieldsContainer: document.getElementById('createFieldsContainer'),
  createFeedback: document.getElementById('createFeedback'),
  btnCancelCreate: document.getElementById('btnCancelCreate'),
  btnSubmitCreate: document.getElementById('btnSubmitCreate')
};

// --- INITIALIZATION ---
document.addEventListener('DOMContentLoaded', async () => {
  setupNavigation();
  setupConnectionHandlers();
  setupSyncHandlers();
  setupExplorerHandlers();
  setupHistoryHandlers();
  setupModal();
  setupCreateRecordHandlers();

  await checkConnectionStatus();
  await refreshMetricsAndTables();
  await loadSyncStates();
  await loadHistory();
});

// --- NAVIGATION & TABS ---
function setupNavigation() {
  el.tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      const targetId = tab.getAttribute('data-tab');
      switchTab(targetId);
    });
  });
}

function switchTab(tabId) {
  state.activeTab = tabId;
  el.tabs.forEach(t => t.classList.toggle('active', t.getAttribute('data-tab') === tabId));
  el.panels.forEach(p => p.classList.toggle('active', p.id === tabId));

  if (tabId === 'tabExplorer') {
    loadExplorerData();
  } else if (tabId === 'tabHistory') {
    loadHistory();
  } else if (tabId === 'tabSync') {
    loadSyncStates();
  }
}

// --- CONNECTION MANAGEMENT ---
function setupConnectionHandlers() {
  let selectedMode = 'mock';

  el.authModeBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      el.authModeBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      selectedMode = btn.getAttribute('data-mode');

      if (el.fieldsEca) el.fieldsEca.classList.toggle('hidden', selectedMode !== 'eca');
      if (el.fieldsMock) el.fieldsMock.classList.toggle('hidden', selectedMode !== 'mock');
      if (el.fieldsPassword) el.fieldsPassword.classList.toggle('hidden', selectedMode !== 'password');
    });
  });

  el.btnConnectSalesforce.addEventListener('click', async () => {
    el.connectionFeedback.textContent = 'Connecting...';
    el.connectionFeedback.style.color = '#38bdf8';
    el.btnConnectSalesforce.disabled = true;

    try {
      const payload = { mode: selectedMode };
      if (selectedMode === 'eca') {
        payload.instanceUrl = el.ecaInstanceUrl.value.trim();
        payload.clientId = el.ecaClientId.value.trim();
        payload.clientSecret = el.ecaClientSecret.value.trim();
      } else if (selectedMode === 'password') {
        payload.loginUrl = el.sfLoginUrl.value;
        payload.username = el.sfUsername.value.trim();
        payload.password = el.sfPassword.value;
        payload.securityToken = el.sfSecurityToken.value.trim();
      }

      const res = await fetch('/api/auth/connect', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const data = await res.json();

      if (!data.success) throw new Error(data.error);

      el.connectionFeedback.textContent = data.data.message || 'Connected successfully!';
      el.connectionFeedback.style.color = '#34d399';
      await checkConnectionStatus();
      setTimeout(() => switchTab('tabSync'), 1000);
    } catch (err) {
      el.connectionFeedback.textContent = `Error: ${err.message}`;
      el.connectionFeedback.style.color = '#f87171';
    } finally {
      el.btnConnectSalesforce.disabled = false;
    }
  });

  el.btnDisconnectSalesforce.addEventListener('click', async () => {
    try {
      await fetch('/api/auth/disconnect', { method: 'POST' });
      await checkConnectionStatus();
    } catch (err) {
      console.error(err);
    }
  });
}

async function checkConnectionStatus() {
  try {
    const res = await fetch('/api/auth/status');
    const json = await res.json();
    const conn = json.data;
    state.connection = conn;

    const pill = el.connectionStatusPill;
    const text = el.connectionStatusText;
    const envTag = el.syncActiveEnvTag;

    if (!conn.connected) {
      pill.className = 'connection-status-pill disconnected';
      text.textContent = 'Disconnected';
      envTag.innerHTML = `<span class="badge badge-danger">Disconnected</span>`;
      el.btnDisconnectSalesforce.classList.add('hidden');
      el.mockSimulatorBox.classList.add('hidden');
    } else if (conn.isMock) {
      pill.className = 'connection-status-pill mock';
      text.textContent = 'Mock Sandbox Active';
      envTag.innerHTML = `<span class="badge badge-info">Mock Sandbox</span>`;
      el.btnDisconnectSalesforce.classList.remove('hidden');
      el.mockSimulatorBox.classList.remove('hidden');
    } else if (conn.mode === 'eca') {
      pill.className = 'connection-status-pill';
      text.textContent = `External Client App Active`;
      envTag.innerHTML = `<span class="badge badge-success">External Client App</span>`;
      el.btnDisconnectSalesforce.classList.remove('hidden');
      el.mockSimulatorBox.classList.add('hidden');
    } else {
      pill.className = 'connection-status-pill';
      text.textContent = `Connected (${conn.username || 'Live'})`;
      envTag.innerHTML = `<span class="badge badge-success">Live Salesforce</span>`;
      el.btnDisconnectSalesforce.classList.remove('hidden');
      el.mockSimulatorBox.classList.add('hidden');
    }
  } catch (err) {
    el.connectionStatusPill.className = 'connection-status-pill disconnected';
    el.connectionStatusText.textContent = 'Offline';
  } finally {
    updateCreateButtonState();
  }
}

// --- SYNC ORCHESTRATION ---
function setupSyncHandlers() {
  el.btnSelectAllObjects.addEventListener('click', () => {
    document.querySelectorAll('.obj-checkbox').forEach(cb => cb.checked = true);
  });

  el.btnDeselectAllObjects.addEventListener('click', () => {
    document.querySelectorAll('.obj-checkbox').forEach(cb => cb.checked = false);
  });

  el.btnClearLogs.addEventListener('click', () => {
    el.syncTerminalBody.innerHTML = '';
  });

  // --- Filter toggle & clear ---
  el.btnToggleFilters.addEventListener('click', () => {
    const panel = el.filterPanel;
    const visible = panel.style.display !== 'none';
    panel.style.display = visible ? 'none' : 'flex';
    el.btnToggleFilters.textContent = visible ? 'Show Filters ▼' : 'Hide Filters ▲';
  });

  el.btnClearFilters.addEventListener('click', () => {
    el.filterName.value = '';
    el.filterCreatedFrom.value = '';
    el.filterCreatedTo.value = '';
    el.filterModifiedFrom.value = '';
    el.filterModifiedTo.value = '';
  });

  el.btnStartSync.addEventListener('click', async () => {
    const selectedObjects = Array.from(document.querySelectorAll('.obj-checkbox:checked')).map(cb => cb.value);
    if (selectedObjects.length === 0) {
      alert('Please select at least one Salesforce object to sync.');
      return;
    }

    const mode = el.syncModeFull.checked ? 'full' : 'incremental';

    // Collect filter values
    const filters = {};
    if (el.filterName.value.trim()) filters.name = el.filterName.value.trim();
    if (el.filterCreatedFrom.value) filters.createdFrom = el.filterCreatedFrom.value;
    if (el.filterCreatedTo.value) filters.createdTo = el.filterCreatedTo.value;
    if (el.filterModifiedFrom.value) filters.modifiedFrom = el.filterModifiedFrom.value;
    if (el.filterModifiedTo.value) filters.modifiedTo = el.filterModifiedTo.value;

    el.btnStartSync.disabled = true;
    el.btnStartSync.querySelector('span').textContent = 'Syncing...';

    try {
      const payload = { objects: selectedObjects, mode };
      if (Object.keys(filters).length > 0) payload.filters = filters;

      const res = await fetch('/api/sync/run', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (!data.success) throw new Error(data.error);

      startSyncPolling();
    } catch (err) {
      alert(`Sync initiation failed: ${err.message}`);
      el.btnStartSync.disabled = false;
      el.btnStartSync.querySelector('span').textContent = 'Start Sync Now';
    }
  });

  el.btnSimulateUpdate.addEventListener('click', async () => {
    try {
      const res = await fetch('/api/mock/simulate-update', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ objectName: 'Account' })
      });
      const data = await res.json();
      if (data.success) {
        appendTerminalLog(`[Mock Generator] ${data.message} (${data.record.Name})`, 'success');
      }
    } catch (err) {
      console.error(err);
    }
  });
}

function startSyncPolling() {
  if (state.syncPollingInterval) clearInterval(state.syncPollingInterval);

  state.syncPollingInterval = setInterval(async () => {
    try {
      const res = await fetch('/api/sync/status');
      const json = await res.json();
      const job = json.data;

      el.jobStatusBadge.className = `status-indicator-badge ${job.status}`;
      el.jobStatusBadge.textContent = job.status;

      el.progressBarFill.style.width = `${job.progressPercent}%`;
      el.progressPercentLabel.textContent = `${job.progressPercent}%`;
      el.currentTaskLabel.textContent = job.currentObject 
        ? `Syncing ${job.currentObject}...` 
        : (job.status === 'completed' ? 'Synchronization complete!' : 'Ready');

      el.statUpserted.textContent = job.totalRecordsSynced || 0;
      el.statObjectsDone.textContent = `${Object.keys(job.details).length} / ${job.objects.length}`;

      let totalFetched = 0;
      for (const key of Object.keys(job.details)) {
        if (job.details[key].fetched) totalFetched += job.details[key].fetched;
      }
      el.statFetched.textContent = totalFetched;

      renderTerminalLogs(job.logs);

      if (job.status === 'completed' || job.status === 'failed') {
        clearInterval(state.syncPollingInterval);
        state.syncPollingInterval = null;
        el.btnStartSync.disabled = false;
        el.btnStartSync.querySelector('span').textContent = 'Start Sync Now';

        await refreshMetricsAndTables();
        await loadSyncStates();
        await loadHistory();
      }
    } catch (err) {
      console.error('Polling error:', err);
    }
  }, 750);
}

function renderTerminalLogs(logs = []) {
  el.syncTerminalBody.innerHTML = logs.map(l => {
    return `<div class="term-line ${l.type}"><span class="term-time">[${l.timestamp}]</span> ${escapeHtml(l.message)}</div>`;
  }).join('');
  el.syncTerminalBody.scrollTop = el.syncTerminalBody.scrollHeight;
}

function appendTerminalLog(message, type = 'info') {
  const line = document.createElement('div');
  line.className = `term-line ${type}`;
  line.innerHTML = `<span class="term-time">[${new Date().toLocaleTimeString()}]</span> ${escapeHtml(message)}`;
  el.syncTerminalBody.appendChild(line);
  el.syncTerminalBody.scrollTop = el.syncTerminalBody.scrollHeight;
}

async function loadSyncStates() {
  try {
    const res = await fetch('/api/sync/states');
    const json = await res.json();
    const states = json.data || [];

    if (states.length === 0) {
      el.objectSummaryCards.innerHTML = `
        <div class="summary-card" style="grid-column: 1 / -1;">
          <div class="summary-card-info">
            <h4>No Objects Synchronized Yet</h4>
            <span class="summary-card-sub">Run a sync above to create local database tables.</span>
          </div>
        </div>
      `;
      return;
    }

    el.objectSummaryCards.innerHTML = states.map(s => {
      const formattedDate = s.last_sync_timestamp ? new Date(s.last_sync_timestamp).toLocaleString() : 'Never';
      return `
        <div class="summary-card">
          <div class="summary-card-info">
            <h4>${s.object_name}</h4>
            <span class="summary-card-sub">Last Sync: ${formattedDate} (${s.last_sync_mode || 'N/A'})</span>
          </div>
          <div class="summary-card-count">
            ${s.total_records}
          </div>
        </div>
      `;
    }).join('');
  } catch (err) {
    console.error(err);
  }
}

// --- LOCAL DATA EXPLORER ---
function setupExplorerHandlers() {
  let searchTimeout;
  el.searchInput.addEventListener('input', (e) => {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => {
      state.explorerSearch = e.target.value;
      state.explorerPage = 1;
      loadExplorerData();
    }, 300);
  });

  el.btnPrevPage.addEventListener('click', () => {
    if (state.explorerPage > 1) {
      state.explorerPage--;
      loadExplorerData();
    }
  });

  el.btnNextPage.addEventListener('click', () => {
    state.explorerPage++;
    loadExplorerData();
  });

  el.btnRefreshData.addEventListener('click', () => {
    loadExplorerData();
  });

  el.btnExportCsv.addEventListener('click', () => {
    window.location.href = `/api/export/${state.activeExplorerTable}?format=csv`;
  });

  el.btnExportJson.addEventListener('click', () => {
    window.location.href = `/api/export/${state.activeExplorerTable}?format=json`;
  });
}

async function refreshMetricsAndTables() {
  try {
    const res = await fetch('/api/data/tables');
    const json = await res.json();
    const tables = json.data || [];
    state.availableTables = tables;

    let totalRecords = 0;
    tables.forEach(t => totalRecords += t.count);

    el.headerDbRecords.textContent = `${totalRecords} records`;
    el.headerDbTables.textContent = `${tables.length} tables`;

    if (tables.length > 0) {
      el.tableSwitcher.innerHTML = tables.map(t => {
        const isActive = t.objectName.toLowerCase() === state.activeExplorerTable.toLowerCase();
        return `
          <button class="table-pill ${isActive ? 'active' : ''}" data-object="${t.objectName}">
            ${t.objectName} <span style="opacity: 0.7">(${t.count})</span>
          </button>
        `;
      }).join('');

      el.tableSwitcher.querySelectorAll('.table-pill').forEach(btn => {
        btn.addEventListener('click', () => {
          state.activeExplorerTable = btn.getAttribute('data-object');
          state.explorerPage = 1;
          state.explorerSearch = '';
          el.searchInput.value = '';
          el.tableSwitcher.querySelectorAll('.table-pill').forEach(b => b.classList.remove('active'));
          btn.classList.add('active');
          loadExplorerData();
        });
      });
    } else {
      el.tableSwitcher.innerHTML = `<span style="color: var(--text-muted); font-size: 0.85rem;">No local tables found. Run a sync first!</span>`;
    }
  } catch (err) {
    console.error(err);
  }
}

async function loadExplorerData() {
  if (!state.activeExplorerTable) return;

  try {
    const params = new URLSearchParams({
      page: state.explorerPage,
      limit: state.explorerLimit,
      search: state.explorerSearch,
      sortBy: state.explorerSortBy,
      sortOrder: state.explorerSortOrder
    });

    const res = await fetch(`/api/data/${state.activeExplorerTable}?${params}`);
    const json = await res.json();
    const data = json.data;

    if (!data.columns || data.columns.length === 0) {
      el.localDataTableHead.innerHTML = '<tr><th>No Data</th></tr>';
      el.localDataTableBody.innerHTML = '<tr><td class="text-center">No records in this table. Run a sync to populate.</td></tr>';
      el.paginationInfo.textContent = 'Showing 0 records';
      return;
    }

    const displayCols = data.columns.slice(0, 7);

    el.localDataTableHead.innerHTML = `
      <tr>
        ${displayCols.map(c => `<th>${c}</th>`).join('')}
        <th style="text-align: right;">Action</th>
      </tr>
    `;

    if (data.records.length === 0) {
      el.localDataTableBody.innerHTML = `<tr><td colspan="${displayCols.length + 1}" class="text-center">No records match your criteria.</td></tr>`;
    } else {
      el.localDataTableBody.innerHTML = data.records.map(row => {
        return `
          <tr>
            ${displayCols.map(col => {
              const val = row[col];
              if (col === 'Id') {
                return `<td class="cell-id">${escapeHtml(String(val || ''))}</td>`;
              }
              if (col.toLowerCase().includes('date') || col.toLowerCase().includes('stamp')) {
                return `<td class="cell-date">${val ? new Date(val).toLocaleString() : '-'}</td>`;
              }
              return `<td>${escapeHtml(String(val ?? '-'))}</td>`;
            }).join('')}
            <td style="text-align: right;">
              <button class="btn btn-secondary btn-sm btn-view-json" data-id="${row.Id}">
                View JSON
              </button>
            </td>
          </tr>
        `;
      }).join('');

      document.querySelectorAll('.btn-view-json').forEach(btn => {
        btn.addEventListener('click', () => {
          const id = btn.getAttribute('data-id');
          openRecordModal(state.activeExplorerTable, id);
        });
      });
    }

    const startIdx = data.total === 0 ? 0 : (data.page - 1) * data.limit + 1;
    const endIdx = Math.min(data.page * data.limit, data.total);
    el.paginationInfo.textContent = `Showing ${startIdx} to ${endIdx} of ${data.total} records`;
    el.pageIndicator.textContent = `Page ${data.page} of ${Math.max(1, data.totalPages)}`;
    el.btnPrevPage.disabled = data.page <= 1;
    el.btnNextPage.disabled = data.page >= data.totalPages;

  } catch (err) {
    console.error('Failed to load explorer data:', err);
  }
}

// --- SYNC HISTORY & AUDIT ---
function setupHistoryHandlers() {
  el.btnRefreshHistory.addEventListener('click', loadHistory);
}

async function loadHistory() {
  try {
    const res = await fetch('/api/sync/history?limit=50');
    const json = await res.json();
    const history = json.data || [];

    if (history.length === 0) {
      el.syncHistoryTableBody.innerHTML = `<tr><td colspan="9" class="text-center">No sync history recorded yet.</td></tr>`;
      return;
    }

    el.syncHistoryTableBody.innerHTML = history.map(h => {
      const statusBadge = h.status === 'SUCCESS' 
        ? '<span class="badge badge-success">SUCCESS</span>' 
        : (h.status === 'RUNNING' ? '<span class="badge badge-info">RUNNING</span>' : '<span class="badge badge-danger">ERROR</span>');
      
      const startTime = h.start_time ? new Date(h.start_time).toLocaleString() : '-';
      const duration = h.duration_ms ? `${h.duration_ms} ms` : '-';

      return `
        <tr>
          <td class="cell-id">#${h.id}</td>
          <td><strong>${h.object_name}</strong></td>
          <td><span class="badge badge-sm">${h.sync_mode}</span></td>
          <td>${statusBadge}</td>
          <td style="font-family: var(--font-mono);">${h.records_fetched || 0}</td>
          <td style="font-family: var(--font-mono);">${h.records_upserted || 0}</td>
          <td style="font-family: var(--font-mono);">${duration}</td>
          <td class="cell-date">${startTime}</td>
          <td style="color: #f87171; font-size: 0.75rem;">${escapeHtml(h.error_message || '-')}</td>
        </tr>
      `;
    }).join('');
  } catch (err) {
    console.error(err);
  }
}

// --- MODAL ---
function setupModal() {
  el.btnCloseModal.addEventListener('click', () => {
    el.recordModal.classList.remove('open');
  });

  el.recordModal.addEventListener('click', (e) => {
    if (e.target === el.recordModal) {
      el.recordModal.classList.remove('open');
    }
  });

  el.btnCopyJson.addEventListener('click', () => {
    navigator.clipboard.writeText(el.modalJsonDisplay.textContent);
    el.btnCopyJson.textContent = 'Copied!';
    setTimeout(() => el.btnCopyJson.textContent = 'Copy JSON', 1500);
  });
}

async function openRecordModal(objectName, id) {
  try {
    const res = await fetch(`/api/data/${objectName}/${id}`);
    const json = await res.json();
    const rec = json.data;

    el.modalRecordTitle.textContent = `${objectName} Record`;
    el.modalRecordId.textContent = rec.Id;

    const payload = rec._parsed_raw_data || rec;
    el.modalJsonDisplay.textContent = JSON.stringify(payload, null, 2);
    el.recordModal.classList.add('open');
  } catch (err) {
    alert('Failed to load record details');
  }
}

function escapeHtml(str) {
  if (typeof str !== 'string') return str;
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// --- CREATE RECORD ---
let cachedCreatableFields = {};

function setupCreateRecordHandlers() {
  // Open modal
  el.btnCreateRecord.addEventListener('click', () => {
    el.createRecordModal.classList.add('open');
    el.createFeedback.className = 'create-feedback';
    el.createFeedback.textContent = '';
    el.createObjectSelector.value = state.activeExplorerTable || '';
    if (el.createObjectSelector.value) {
      loadCreatableFields(el.createObjectSelector.value);
    }
  });

  // Close modal
  el.btnCloseCreateModal.addEventListener('click', closeCreateModal);
  el.btnCancelCreate.addEventListener('click', closeCreateModal);
  el.createRecordModal.addEventListener('click', (e) => {
    if (e.target === el.createRecordModal) closeCreateModal();
  });

  // Object selector change
  el.createObjectSelector.addEventListener('change', (e) => {
    const objectName = e.target.value;
    if (objectName) {
      loadCreatableFields(objectName);
    } else {
      el.createFieldsContainer.innerHTML = '<p class="text-muted" style="text-align:center; padding: 2rem 0;">Select an object above to load its fields.</p>';
      el.btnSubmitCreate.disabled = true;
    }
  });

  // Submit
  el.btnSubmitCreate.addEventListener('click', submitCreateRecord);
}

function closeCreateModal() {
  el.createRecordModal.classList.remove('open');
}

function updateCreateButtonState() {
  // Enable button only when connected to a real SF org (not mock)
  const isLive = state.connection && state.connection.connected && !state.connection.isMock;
  el.btnCreateRecord.disabled = !isLive;
  el.btnCreateRecord.title = isLive
    ? 'Create a new record in Salesforce'
    : 'Connect to a real Salesforce org to create records';
}

async function loadCreatableFields(objectName) {
  el.createFieldsLoading.style.display = 'flex';
  el.createFieldsContainer.innerHTML = '';
  el.btnSubmitCreate.disabled = true;
  el.createFeedback.className = 'create-feedback';
  el.createFeedback.textContent = '';

  try {
    // Use cache if available
    if (cachedCreatableFields[objectName]) {
      renderCreateFields(cachedCreatableFields[objectName]);
      el.createFieldsLoading.style.display = 'none';
      return;
    }

    const res = await fetch(`/api/objects/${objectName}/fields`);
    const json = await res.json();
    if (!json.success) throw new Error(json.error);

    cachedCreatableFields[objectName] = json.data;
    renderCreateFields(json.data);
  } catch (err) {
    el.createFieldsContainer.innerHTML = `<p style="color: #f87171; text-align:center; padding: 1rem;">Failed to load fields: ${escapeHtml(err.message)}</p>`;
  } finally {
    el.createFieldsLoading.style.display = 'none';
  }
}

function renderCreateFields(fields) {
  // Separate required fields from optional
  const required = fields.filter(f => f.required);
  const optional = fields.filter(f => !f.required);

  // Prioritize common fields at the top of optional
  const commonFieldNames = ['FirstName', 'LastName', 'Name', 'Email', 'Phone', 'Title', 'Company', 'Description', 'Website', 'Industry'];
  const prioritized = [];
  const remaining = [];
  for (const f of optional) {
    if (commonFieldNames.includes(f.name)) {
      prioritized.push(f);
    } else {
      remaining.push(f);
    }
  }

  // Only show the first 30 optional fields to avoid overwhelming the form
  const shownOptional = [...prioritized, ...remaining].slice(0, 30);

  let html = '<div class="create-fields-grid">';

  // Required fields section
  if (required.length > 0) {
    html += '<div class="create-section-label">Required Fields</div>';
    for (const field of required) {
      html += renderFieldInput(field, true);
    }
  }

  // Optional fields section
  if (shownOptional.length > 0) {
    html += '<div class="create-section-label">Optional Fields</div>';
    for (const field of shownOptional) {
      html += renderFieldInput(field, false);
    }
  }

  html += '</div>';
  el.createFieldsContainer.innerHTML = html;
  el.btnSubmitCreate.disabled = false;
}

function renderFieldInput(field, isRequired) {
  const requiredStar = isRequired ? '<span class="required-star">*</span>' : '';
  const reqAttr = isRequired ? 'required' : '';
  const label = `<label>${escapeHtml(field.label)}${requiredStar}</label>`;

  let input = '';

  if (field.type === 'picklist' || field.type === 'multipicklist') {
    const options = field.picklistValues.map(p =>
      `<option value="${escapeHtml(p.value)}">${escapeHtml(p.label)}</option>`
    ).join('');
    input = `<select data-field="${field.name}" ${reqAttr}><option value="">-- Select --</option>${options}</select>`;
  } else if (field.type === 'boolean') {
    input = `<select data-field="${field.name}" ${reqAttr}><option value="">-- Select --</option><option value="true">Yes</option><option value="false">No</option></select>`;
  } else if (field.type === 'textarea' || field.type === 'url' || (field.length && field.length > 255)) {
    input = `<textarea data-field="${field.name}" ${reqAttr} placeholder="${escapeHtml(field.label)}"></textarea>`;
  } else if (field.type === 'date') {
    input = `<input type="date" data-field="${field.name}" ${reqAttr}>`;
  } else if (field.type === 'datetime') {
    input = `<input type="datetime-local" data-field="${field.name}" ${reqAttr}>`;
  } else if (field.type === 'double' || field.type === 'currency' || field.type === 'percent' || field.type === 'int') {
    input = `<input type="number" step="any" data-field="${field.name}" ${reqAttr} placeholder="${escapeHtml(field.label)}">`;
  } else if (field.type === 'email') {
    input = `<input type="email" data-field="${field.name}" ${reqAttr} placeholder="${escapeHtml(field.label)}">`;
  } else if (field.type === 'phone') {
    input = `<input type="tel" data-field="${field.name}" ${reqAttr} placeholder="${escapeHtml(field.label)}">`;
  } else {
    input = `<input type="text" data-field="${field.name}" ${reqAttr} placeholder="${escapeHtml(field.label)}" ${field.length ? `maxlength="${field.length}"` : ''}>`;
  }

  const isWide = field.type === 'textarea' || (field.length && field.length > 255);
  return `<div class="create-field-group${isWide ? ' full-width' : ''}">${label}${input}</div>`;
}

async function submitCreateRecord() {
  const objectName = el.createObjectSelector.value;
  if (!objectName) {
    showCreateFeedback('Please select an object type.', 'error');
    return;
  }

  // Collect form values
  const fieldElements = el.createFieldsContainer.querySelectorAll('[data-field]');
  const recordData = {};

  for (const inputEl of fieldElements) {
    const fieldName = inputEl.getAttribute('data-field');
    let value = inputEl.value.trim();

    if (value === '') continue; // Skip empty optional fields

    // Type conversions
    if (inputEl.type === 'number') {
      value = parseFloat(value);
      if (isNaN(value)) continue;
    } else if (inputEl.tagName === 'SELECT' && (value === 'true' || value === 'false')) {
      value = value === 'true';
    } else if (inputEl.type === 'datetime-local' && value) {
      value = new Date(value).toISOString();
    }

    recordData[fieldName] = value;
  }

  // Check required fields
  const requiredEls = el.createFieldsContainer.querySelectorAll('[data-field][required]');
  for (const reqEl of requiredEls) {
    if (!reqEl.value.trim()) {
      const label = reqEl.closest('.create-field-group')?.querySelector('label')?.textContent || reqEl.getAttribute('data-field');
      showCreateFeedback(`Required field missing: ${label.replace('*', '')}`, 'error');
      reqEl.focus();
      return;
    }
  }

  if (Object.keys(recordData).length === 0) {
    showCreateFeedback('Please fill in at least one field.', 'error');
    return;
  }

  // Disable button while submitting
  el.btnSubmitCreate.disabled = true;
  el.btnSubmitCreate.querySelector('svg')?.remove();
  const origText = el.btnSubmitCreate.textContent;
  el.btnSubmitCreate.textContent = 'Creating...';

  try {
    const res = await fetch(`/api/data/${objectName}/create`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(recordData)
    });
    const json = await res.json();

    if (!json.success) throw new Error(json.error);

    showCreateFeedback(`✓ ${json.data.message} (ID: ${json.data.id})`, 'success');

    // Refresh the explorer table
    state.activeExplorerTable = objectName;
    await refreshMetricsAndTables();
    await loadExplorerData();

    // Close modal after a brief delay
    setTimeout(() => closeCreateModal(), 2000);
  } catch (err) {
    showCreateFeedback(`✕ ${err.message}`, 'error');
  } finally {
    el.btnSubmitCreate.disabled = false;
    el.btnSubmitCreate.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M22 2L11 13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
      Create in Salesforce
    `;
  }
}

function showCreateFeedback(msg, type) {
  el.createFeedback.textContent = msg;
  el.createFeedback.className = `create-feedback ${type}`;
}
