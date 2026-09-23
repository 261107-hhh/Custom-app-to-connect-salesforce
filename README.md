# Salesforce Local Database Sync Application

A modern, high-performance application to extract and synchronize data from **Salesforce** (standard & custom objects) into a local **SQLite database**, complete with automated schema creation, incremental change tracking (`SystemModstamp`), and a responsive web dashboard.

---

## Key Capabilities

1. **Flexible Authentication**:
   - **Interactive Mock Sandbox**: Instantly test synchronization, schemas, and incremental delta updates without needing live Salesforce credentials.
   - **Direct Password Flow**: Connect with Salesforce Username, Password, and Security Token for Production (`login.salesforce.com`) or Sandbox (`test.salesforce.com`).
   - **OAuth 2.0 / Bearer Session**: Connect using Instance URL and OAuth Access Token.
2. **Dual Sync Modes**:
   - **Incremental Sync**: Only pulls records created or updated since the last sync using `SystemModstamp` filtering, minimizing API usage and execution time.
   - **Full Sync**: Re-fetches and upserts entire object tables.
3. **Dynamic Schema & Fidelity**:
   - Automatically creates local SQLite tables matching Salesforce object schemas (`sf_account`, `sf_contact`, `sf_opportunity`, `sf_lead`, etc.).
   - Dynamically adds columns for discovered fields.
   - Preserves complete original Salesforce records with all custom fields inside a high-speed `raw_data` JSON column.
4. **Interactive Web Dashboard**:
   - **Sync Center**: Choose objects, select sync strategy, watch live progress bars, metrics, and streaming console logs.
   - **Local Data Explorer**: Switch between synced tables, search records with debounced queries, paginate, and click "View JSON" to inspect complete record payloads.
   - **Audit & History**: Track every sync job's execution timestamp, duration in milliseconds, record counts, and status.
   - **Exporting**: One-click export to CSV or JSON.
5. **Headless CLI & Background Automation**:
   - Run syncs headlessly via command-line or cron jobs (`npm run sync`).

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                 Web Dashboard (Port 3000)                   │
│   (Connection Manager, Object Selector, Sync, Data Viewer)  │
└──────────────────────────────┬──────────────────────────────┘
                               │ REST API
┌──────────────────────────────▼──────────────────────────────┐
│                    Sync Engine & Server                     │
│  - Auth Manager (Mock / Password / OAuth 2.0)               │
│  - Salesforce Client (JSforce & SOQL Query Engine)          │
│  - Schema Introspection & Dynamic Table Creator             │
│  - Sync Service (Incremental Modstamp & Batch Upsert)       │
└──────────────────────────────┬──────────────────────────────┘
                               │ SQL Transactions
┌──────────────────────────────▼──────────────────────────────┐
│                   Local SQLite Database                     │
│  - Object Tables (sf_account, sf_contact, sf_opportunity...)│
│  - raw_data JSON storage for all custom fields              │
│  - Audit logs (_sync_history) & state (_sync_state)         │
└─────────────────────────────────────────────────────────────┘
```

---

## Getting Started

### 1. Launch the Web Dashboard

```bash
npm start
```
Open your browser at [http://localhost:3000](http://localhost:3000).

### 2. Run Headless Sync via CLI

```bash
# Sync specific objects in incremental mode
node src/cli.js --objects=Account,Contact,Opportunity --incremental

# Run full sync
node src/cli.js --objects=Account,Lead --full
```

### 3. Run Automated Tests

```bash
npm test
```

---

## Configuring Live Salesforce Credentials

You can enter your credentials directly in the **Salesforce Connection** tab in the web dashboard, or define them in a `.env` file:

```env
PORT=3000

# Salesforce Login
SF_LOGIN_URL=https://login.salesforce.com
SF_USERNAME=your_salesforce_username@example.com
SF_PASSWORD=your_salesforce_password
SF_SECURITY_TOKEN=your_security_token
```

### How to obtain a Security Token in Salesforce:
1. Log in to Salesforce.
2. Click your user avatar in the top-right and select **Settings**.
3. In the left navigation, go to **My Personal Information** > **Reset My Security Token**.
4. Click **Reset Security Token**. Salesforce will email you the new token immediately.

---

## Project Structure

```
salesforce-local-sync/
├── data/
│   └── salesforce_local.db      # SQLite database file (WAL mode)
├── public/
│   ├── css/
│   │   └── style.css            # Dark-theme design system & styles
│   ├── js/
│   │   └── app.js               # Frontend application controller
│   └── index.html               # Main dashboard UI
├── src/
│   ├── db/
│   │   └── database.js          # SQLite manager with dynamic schema & upserts
│   ├── mock/
│   │   └── mockSalesforce.js    # Built-in realistic Salesforce simulator
│   ├── salesforce/
│   │   ├── client.js            # JSforce connection & authentication
│   │   └── syncService.js       # Sync engine & incremental tracking
│   ├── cli.js                   # Command-line sync runner
│   └── server.js                # Express REST API & static file server
├── tests/
│   └── test.js                  # Automated integration tests
├── .env.example
└── package.json
```
