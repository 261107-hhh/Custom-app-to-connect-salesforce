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
  btnModalTabRelations: document.getElementById('btnModalTabRelations'),
  btnModalTabJson: document.getElementById('btnModalTabJson'),
  modalRelationsPanel: document.getElementById('modalRelationsPanel'),
  modalJsonPanel: document.getElementById('modalJsonPanel'),
  modalRelationsLoading: document.getElementById('modalRelationsLoading'),
  modalRelationsContainer: document.getElementById('modalRelationsContainer'),

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

    const isAccountTable = state.activeExplorerTable.toLowerCase() === 'account';
    const displayCols = data.columns.filter(c => !c.startsWith('_')).slice(0, 7);

    el.localDataTableHead.innerHTML = `
      <tr>
        ${displayCols.map(c => `<th>${c === 'Account_Name' ? 'Related Account' : c}</th>`).join('')}
        ${isAccountTable ? '<th>Related</th>' : ''}
        <th style="text-align: right;">Action</th>
      </tr>
    `;

    if (data.records.length === 0) {
      el.localDataTableBody.innerHTML = `<tr><td colspan="${displayCols.length + (isAccountTable ? 2 : 1)}" class="text-center">No records match your criteria.</td></tr>`;
    } else {
      el.localDataTableBody.innerHTML = data.records.map(row => {
        let relatedHtml = '';
        if (isAccountTable) {
          const contactCount = row._contact_count ?? 0;
          const oppCount = row._opportunity_count ?? 0;
          relatedHtml = `
            <td>
              <span class="rel-stat-pill btn-show-account-rel" data-id="${row.Id}" title="${contactCount} Contacts">👥 ${contactCount}</span>
              <span class="rel-stat-pill btn-show-account-rel" data-id="${row.Id}" title="${oppCount} Deals">💼 ${oppCount}</span>
            </td>
          `;
        }

        return `
          <tr>
            ${displayCols.map(col => {
              const val = row[col];
              if (col === 'Id') {
                return `<td class="cell-id">${escapeHtml(String(val || ''))}</td>`;
              }
              if (col === 'Account_Name' || (col === 'AccountId' && row.AccountId)) {
                const accLabel = row.Account_Name || row.AccountId;
                return `<td><span class="account-pill" data-account-id="${escapeHtml(row.AccountId)}" title="View Account Details">🏢 ${escapeHtml(accLabel)}</span></td>`;
              }
              if (col.toLowerCase().includes('date') || col.toLowerCase().includes('stamp')) {
                return `<td class="cell-date">${val ? new Date(val).toLocaleString() : '-'}</td>`;
              }
              return `<td>${escapeHtml(String(val ?? '-'))}</td>`;
            }).join('')}
            ${relatedHtml}
            <td style="text-align: right;">
              <button class="btn btn-secondary btn-sm btn-view-details" data-id="${row.Id}">
                View Details
              </button>
            </td>
          </tr>
        `;
      }).join('');

      document.querySelectorAll('.btn-view-details').forEach(btn => {
        btn.addEventListener('click', () => {
          const id = btn.getAttribute('data-id');
          openRecordModal(state.activeExplorerTable, id);
        });
      });

      document.querySelectorAll('.btn-show-account-rel').forEach(btn => {
        btn.addEventListener('click', () => {
          const id = btn.getAttribute('data-id');
          openRecordModal('Account', id);
        });
      });

      document.querySelectorAll('.account-pill').forEach(pill => {
        pill.addEventListener('click', (e) => {
          e.stopPropagation();
          const accId = pill.getAttribute('data-account-id');
          if (accId) {
            openRecordModal('Account', accId);
          }
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

  el.btnModalTabRelations.addEventListener('click', () => {
    switchModalTab('relations');
  });

  el.btnModalTabJson.addEventListener('click', () => {
    switchModalTab('json');
  });
}

function switchModalTab(tab) {
  if (tab === 'relations') {
    el.btnModalTabRelations.classList.add('active');
    el.btnModalTabJson.classList.remove('active');
    el.modalRelationsPanel.style.display = 'block';
    el.modalJsonPanel.style.display = 'none';
  } else {
    el.btnModalTabJson.classList.add('active');
    el.btnModalTabRelations.classList.remove('active');
    el.modalRelationsPanel.style.display = 'none';
    el.modalJsonPanel.style.display = 'block';
  }
}

async function openRecordModal(objectName, id) {
  try {
    switchModalTab('relations');
    el.modalRecordTitle.textContent = `${objectName} Details`;
    el.modalRecordId.textContent = id;
    el.modalRelationsLoading.style.display = 'flex';
    el.modalRelationsContainer.innerHTML = '';
    el.recordModal.classList.add('open');

    // Parallel fetch: full raw record + related bundle
    const [rawRes, relRes] = await Promise.all([
      fetch(`/api/data/${objectName}/${id}`),
      fetch(`/api/data/${objectName}/${id}/related`)
    ]);

    const rawJson = await rawRes.json();
    const relJson = await relRes.json();

    const rec = rawJson.data || {};
    const rel = relJson.data || {};

    const payload = rec._parsed_raw_data || rec;
    el.modalJsonDisplay.textContent = JSON.stringify(payload, null, 2);

    renderModalRelations(objectName, id, rec, rel);
  } catch (err) {
    console.error('Failed to load record details:', err);
    el.modalRelationsContainer.innerHTML = `<p style="color: #f87171; text-align:center; padding: 2rem;">Failed to load details: ${escapeHtml(err.message)}</p>`;
  } finally {
    el.modalRelationsLoading.style.display = 'none';
  }
}

function renderModalRelations(objectName, id, rec, rel) {
  const lower = objectName.toLowerCase();
  let html = '<div class="rel-container">';

  // 1. Overview card (Summary fields)
  html += `
    <div class="rel-overview-card">
      <div class="rel-overview-header">
        <div class="rel-overview-title">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>
          ${escapeHtml(rec.Name || id)}
        </div>
        <span class="badge badge-sm badge-info">${escapeHtml(objectName)}</span>
      </div>
      <div class="rel-meta-grid">
  `;

  if (lower === 'account') {
    html += `
      <div class="rel-meta-item"><span class="rel-meta-label">Type</span><span class="rel-meta-value">${escapeHtml(rec.Type || '-')}</span></div>
      <div class="rel-meta-item"><span class="rel-meta-label">Industry</span><span class="rel-meta-value">${escapeHtml(rec.Industry || '-')}</span></div>
      <div class="rel-meta-item"><span class="rel-meta-label">Phone</span><span class="rel-meta-value">${escapeHtml(rec.Phone || '-')}</span></div>
      <div class="rel-meta-item"><span class="rel-meta-label">Website</span><span class="rel-meta-value">${escapeHtml(rec.Website || '-')}</span></div>
      <div class="rel-meta-item"><span class="rel-meta-label">Billing City</span><span class="rel-meta-value">${escapeHtml(rec.BillingCity || '-')}</span></div>
      <div class="rel-meta-item"><span class="rel-meta-label">Annual Revenue</span><span class="rel-meta-value">${rec.AnnualRevenue ? '$' + Number(rec.AnnualRevenue).toLocaleString() : '-'}</span></div>
    `;
  } else if (lower === 'contact') {
    html += `
      <div class="rel-meta-item"><span class="rel-meta-label">Title</span><span class="rel-meta-value">${escapeHtml(rec.Title || '-')}</span></div>
      <div class="rel-meta-item"><span class="rel-meta-label">Email</span><span class="rel-meta-value">${escapeHtml(rec.Email || '-')}</span></div>
      <div class="rel-meta-item"><span class="rel-meta-label">Phone</span><span class="rel-meta-value">${escapeHtml(rec.Phone || '-')}</span></div>
      <div class="rel-meta-item"><span class="rel-meta-label">Department</span><span class="rel-meta-value">${escapeHtml(rec.Department || '-')}</span></div>
      <div class="rel-meta-item"><span class="rel-meta-label">Created Date</span><span class="rel-meta-value">${rec.CreatedDate ? new Date(rec.CreatedDate).toLocaleDateString() : '-'}</span></div>
    `;
  } else if (lower === 'opportunity') {
    html += `
      <div class="rel-meta-item"><span class="rel-meta-label">Stage</span><span class="rel-meta-value">${escapeHtml(rec.StageName || '-')}</span></div>
      <div class="rel-meta-item"><span class="rel-meta-label">Amount</span><span class="rel-meta-value">${rec.Amount ? '$' + Number(rec.Amount).toLocaleString() : '-'}</span></div>
      <div class="rel-meta-item"><span class="rel-meta-label">Close Date</span><span class="rel-meta-value">${rec.CloseDate || '-'}</span></div>
      <div class="rel-meta-item"><span class="rel-meta-label">Probability</span><span class="rel-meta-value">${rec.Probability ? rec.Probability + '%' : '-'}</span></div>
      <div class="rel-meta-item"><span class="rel-meta-label">Type</span><span class="rel-meta-value">${escapeHtml(rec.Type || '-')}</span></div>
    `;
  } else {
    const sampleKeys = Object.keys(rec).filter(k => !k.startsWith('_') && k !== 'raw_data' && k !== 'Id' && k !== 'Name').slice(0, 6);
    for (const k of sampleKeys) {
      html += `<div class="rel-meta-item"><span class="rel-meta-label">${escapeHtml(k)}</span><span class="rel-meta-value">${escapeHtml(String(rec[k] ?? '-'))}</span></div>`;
    }
  }

  html += `
      </div>
    </div>
  `;

  // 2. Relational sections based on object type
  if (lower === 'account') {
    // Related Contacts Section
    const contacts = rel.contacts || [];
    html += `
      <div class="rel-section-card">
        <div class="rel-section-header">
          <div class="rel-section-title">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
            Related Contacts
            <span class="rel-count-badge">${contacts.length}</span>
          </div>
          <button class="btn btn-secondary btn-sm btn-add-rel-record" data-object="Contact" data-account-id="${id}">
            + Add Contact
          </button>
        </div>
        ${contacts.length === 0 ? '<div class="rel-empty-msg">No contacts linked to this account yet.</div>' : `
          <table class="rel-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Title</th>
                <th>Email</th>
                <th>Phone</th>
                <th style="text-align:right;">Action</th>
              </tr>
            </thead>
            <tbody>
              ${contacts.map(c => `
                <tr>
                  <td><strong>${escapeHtml(c.Name || '-')}</strong></td>
                  <td>${escapeHtml(c.Title || '-')}</td>
                  <td>${escapeHtml(c.Email || '-')}</td>
                  <td>${escapeHtml(c.Phone || '-')}</td>
                  <td style="text-align:right;">
                    <button class="btn btn-secondary btn-sm btn-open-subrecord" data-object="Contact" data-id="${c.Id}">View</button>
                  </td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        `}
      </div>
    `;

    // Related Opportunities Section
    const opps = rel.opportunities || [];
    html += `
      <div class="rel-section-card">
        <div class="rel-section-header">
          <div class="rel-section-title">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
            Related Opportunities
            <span class="rel-count-badge">${opps.length}</span>
          </div>
          <button class="btn btn-secondary btn-sm btn-add-rel-record" data-object="Opportunity" data-account-id="${id}">
            + Add Opportunity
          </button>
        </div>
        ${opps.length === 0 ? '<div class="rel-empty-msg">No opportunities linked to this account yet.</div>' : `
          <table class="rel-table">
            <thead>
              <tr>
                <th>Deal Name</th>
                <th>Stage</th>
                <th>Amount</th>
                <th>Close Date</th>
                <th style="text-align:right;">Action</th>
              </tr>
            </thead>
            <tbody>
              ${opps.map(o => `
                <tr>
                  <td><strong>${escapeHtml(o.Name || '-')}</strong></td>
                  <td><span class="badge badge-sm badge-info">${escapeHtml(o.StageName || '-')}</span></td>
                  <td>${o.Amount ? '$' + Number(o.Amount).toLocaleString() : '-'}</td>
                  <td>${o.CloseDate || '-'}</td>
                  <td style="text-align:right;">
                    <button class="btn btn-secondary btn-sm btn-open-subrecord" data-object="Opportunity" data-id="${o.Id}">View</button>
                  </td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        `}
      </div>
    `;
  } else if (lower === 'contact' || lower === 'opportunity') {
    // Parent Account Card
    const acc = rel.account;
    html += `
      <div class="rel-section-card">
        <div class="rel-section-header">
          <div class="rel-section-title">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
            Mapped Parent Account
          </div>
          ${acc ? `<button class="btn btn-secondary btn-sm btn-open-subrecord" data-object="Account" data-id="${acc.Id}">View Account</button>` : ''}
        </div>
        ${!acc ? '<div class="rel-empty-msg">This record is not mapped to an Account yet.</div>' : `
          <div style="padding: 1.15rem;">
            <div style="display:flex; align-items:center; gap:0.5rem; margin-bottom: 0.75rem;">
              <span class="account-pill" style="font-size: 0.95rem; padding: 0.35rem 0.85rem;" data-account-id="${acc.Id}">
                🏢 ${escapeHtml(acc.Name)}
              </span>
              <span class="badge badge-sm badge-info">${escapeHtml(acc.Type || 'Account')}</span>
            </div>
            <div class="rel-meta-grid">
              <div class="rel-meta-item"><span class="rel-meta-label">Industry</span><span class="rel-meta-value">${escapeHtml(acc.Industry || '-')}</span></div>
              <div class="rel-meta-item"><span class="rel-meta-label">Phone</span><span class="rel-meta-value">${escapeHtml(acc.Phone || '-')}</span></div>
              <div class="rel-meta-item"><span class="rel-meta-label">Website</span><span class="rel-meta-value">${escapeHtml(acc.Website || '-')}</span></div>
              <div class="rel-meta-item"><span class="rel-meta-label">City</span><span class="rel-meta-value">${escapeHtml(acc.BillingCity || '-')}</span></div>
            </div>
          </div>
        `}
      </div>
    `;

    // Sibling Opportunities / Contacts
    if (lower === 'contact' && rel.opportunities && rel.opportunities.length > 0) {
      html += `
        <div class="rel-section-card">
          <div class="rel-section-header">
            <div class="rel-section-title">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
              Account Deals (${rel.opportunities.length})
            </div>
          </div>
          <table class="rel-table">
            <thead><tr><th>Deal Name</th><th>Stage</th><th>Amount</th><th>Close Date</th></tr></thead>
            <tbody>
              ${rel.opportunities.map(o => `
                <tr>
                  <td><strong>${escapeHtml(o.Name || '-')}</strong></td>
                  <td><span class="badge badge-sm badge-info">${escapeHtml(o.StageName || '-')}</span></td>
                  <td>${o.Amount ? '$' + Number(o.Amount).toLocaleString() : '-'}</td>
                  <td>${o.CloseDate || '-'}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
    } else if (lower === 'opportunity' && rel.contacts && rel.contacts.length > 0) {
      html += `
        <div class="rel-section-card">
          <div class="rel-section-header">
            <div class="rel-section-title">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
              Account Contacts (${rel.contacts.length})
            </div>
          </div>
          <table class="rel-table">
            <thead><tr><th>Name</th><th>Title</th><th>Email</th><th>Phone</th></tr></thead>
            <tbody>
              ${rel.contacts.map(c => `
                <tr>
                  <td><strong>${escapeHtml(c.Name || '-')}</strong></td>
                  <td>${escapeHtml(c.Title || '-')}</td>
                  <td>${escapeHtml(c.Email || '-')}</td>
                  <td>${escapeHtml(c.Phone || '-')}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
    }
  }

  html += '</div>';
  el.modalRelationsContainer.innerHTML = html;

  // Setup click handlers inside modal
  el.modalRelationsContainer.querySelectorAll('.btn-open-subrecord').forEach(btn => {
    btn.addEventListener('click', () => {
      const obj = btn.getAttribute('data-object');
      const subId = btn.getAttribute('data-id');
      openRecordModal(obj, subId);
    });
  });

  el.modalRelationsContainer.querySelectorAll('.btn-add-rel-record').forEach(btn => {
    btn.addEventListener('click', () => {
      const obj = btn.getAttribute('data-object');
      const accId = btn.getAttribute('data-account-id');
      openCreateModalWithPrefill(obj, accId);
    });
  });
}

function openCreateModalWithPrefill(objectName, accountId) {
  el.recordModal.classList.remove('open');
  state.prefillAccountId = accountId;
  el.createRecordModal.classList.add('open');
  el.createFeedback.className = 'create-feedback';
  el.createFeedback.textContent = '';
  el.createObjectSelector.value = objectName;
  loadCreatableFields(objectName);
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
  state.prefillAccountId = null;
}

let lookupCache = {};
async function fetchLookupOptions(objectName, fieldName) {
  try {
    let options = lookupCache[objectName];
    if (!options) {
      const res = await fetch(`/api/lookups/${objectName}`);
      const json = await res.json();
      options = json.data || [];
      lookupCache[objectName] = options;
    }
    const select = el.createFieldsContainer.querySelector(`select[data-field="${fieldName}"]`);
    if (!select) return;
    const currentVal = select.value;
    select.innerHTML = `<option value="">-- Select Related Account --</option>` +
      options.map(opt => `<option value="${escapeHtml(opt.id)}">${escapeHtml(opt.name)}</option>`).join('');
    if (state.prefillAccountId && fieldName === 'AccountId') {
      select.value = state.prefillAccountId;
    } else if (currentVal) {
      select.value = currentVal;
    }
  } catch (err) {
    console.error('Failed to load lookups:', err);
  }
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
  const commonFieldNames = ['AccountId', 'FirstName', 'LastName', 'Name', 'Email', 'Phone', 'Title', 'Company', 'Description', 'Website', 'Industry'];
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

  const isAccountLookup = field.name === 'AccountId' || (field.type === 'reference' && field.referenceTo && field.referenceTo.includes('Account'));

  if (isAccountLookup) {
    input = `<select data-field="${field.name}" ${reqAttr} class="form-control select-account-lookup"><option value="">-- Select Related Account --</option></select>`;
    fetchLookupOptions('Account', field.name);
  } else if (field.type === 'picklist' || field.type === 'multipicklist') {
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
