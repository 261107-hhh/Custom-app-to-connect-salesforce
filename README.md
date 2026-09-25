# Salesforce Sync Platform - Spring Boot 3 Backend

A production-grade, enterprise data synchronization platform built with **Java 17**, **Spring Boot 3.3**, **Spring Data JPA**, and **Spring Security (JWT)**.

It seamlessly extracts and synchronizes data from **Salesforce** (standard & custom objects) into a local database (**PostgreSQL** or instant **in-memory H2**), featuring automated schema mapping, relational integrity, incremental change detection (`SystemModstamp`), user audit tracking, single-click CSV/JSON export, and an interactive web dashboard.

---

## Key Capabilities

1. **Flexible Authentication**:
   - **Interactive Mock Sandbox**: Instantly test synchronization, schema changes, and incremental delta updates without live Salesforce credentials.
   - **External Client App (OAuth 2.0 Client Credentials)**: Enterprise-grade OAuth token flow using Client ID and Client Secret.
   - **Direct Password Flow**: Connect with Salesforce Username, Password, and Security Token for Production (`login.salesforce.com`) or Sandbox (`test.salesforce.com`).
   - **OAuth 2.0 Session Token**: Connect using custom Instance URL and active Bearer token.

2. **Dual Synchronization Engines**:
   - **Incremental Sync**: Only pulls records created or modified since the last sync using `SystemModstamp` filtering, minimizing API usage and execution time.
   - **Full Sync**: Re-fetches and upserts entire object tables.
   - **Advanced Query Filters**: Filter by Name, Created Date range, and Modified Date range directly from the dashboard or CLI.

3. **Database & Relational Modeling**:
   - **PostgreSQL / H2 JPA Entities**: Strongly-typed JPA entities for `Account`, `Contact`, `Opportunity`, and `Lead` with bidirectional relationships.
   - **Full Fidelity JSON Preservation**: Stores the complete raw Salesforce payload in a `raw_data` column.
   - **User Audit Tracking**: Attributes records created through the Custom App to authenticated users (`custom_app_created_by`), preserved through subsequent syncs.

4. **Interactive Web Dashboard (Port 8080)**:
   - **Sync Center**: Object selection, incremental/full mode, real-time progress bars, streaming execution logs, and live metrics.
   - **Data Explorer**: Search records with debounced queries, sortable columns, pagination, and relational lookups (Parent Account, Related Contacts, Related Opportunities).
   - **Audit & History**: Track sync logs, record counts, and duration in milliseconds.
   - **Data Export**: Single-click export of any synchronized object to **CSV** or **JSON**.

5. **Headless CLI & Automation**:
   - Run automated headless syncs from command line or scheduled tasks via `run-sync.bat` or Maven.

6. **Interactive API Documentation & Tools**:
   - **Swagger / OpenAPI 3.0**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
   - **H2 Database Console** (Local Profile): [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL: `jdbc:h2:mem:salesforce_sync`)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│              External Frontend (SF Sync FE)                 │
│         (Vite / React / Next.js / Vue / Angular)            │
└──────────────────────────────┬──────────────────────────────┘
                               │ REST API (JSON / JWT / CORS)
┌──────────────────────────────▼──────────────────────────────┐
│             Spring Boot 3 Application Layer                 │
│  - Security: JWT Filter & Stateless Authentication          │
│  - CORS: Full cross-origin support for external frontends   │
│  - Controllers: Auth, Sync, Data, Export, Lookups, Mock     │
│  - Services: SalesforceClient, SalesforceSync, MockSalesforce│
│  - CLI: SalesforceCliRunner (Headless CLI mode)             │
│  - Repositories: Spring Data JPA Repositories               │
└──────────────────────────────┬──────────────────────────────┘
                               │ Hibernate / JDBC
┌──────────────────────────────▼──────────────────────────────┐
│                     Database Layer                          │
│  - Local Dev: In-memory H2 (Zero external setup)            │
│  - Production: PostgreSQL (High performance relational DB)  │
│  - Tables: sf_account, sf_contact, sf_opportunity, sf_lead  │
│  - Audit: _sync_history, _sync_state, _sync_config, _users  │
└─────────────────────────────────────────────────────────────┘
```

---

## Getting Started

### Prerequisites
- **Java 17+** installed (verify with `java -version`)
- Maven (included via `./mvnw` / `mvnw.cmd`)

### Option 1: Instant Local Mode (Zero Setup - H2 In-Memory)
Double-click `run-backend-local.bat` or run:

```cmd
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

Open your browser at:
- Web Dashboard: [http://localhost:8080](http://localhost:8080)
- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- H2 Console: [http://localhost:8080/h2-console](http://localhost:8080/h2-console)

### Option 2: PostgreSQL Production Mode
Ensure PostgreSQL is running, then double-click `run-backend-postgres.bat` or run:

```cmd
.\mvnw.cmd spring-boot:run
```

Environment variables can be provided via `.env` or system environment:
```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/salesforce_sync
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
```

### Option 3: Headless Sync CLI
Run synchronization headlessly without a browser:

```cmd
# Run incremental sync on default objects
run-sync.bat

# Sync specific objects in full mode
run-sync.bat --objects=Account,Contact --full

# Show CLI options
run-sync.bat --help
```

Or via Maven directly:
```cmd
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--sync --objects=Account,Opportunity --incremental --exit"
```

---

## Running Automated Tests

Run the full JUnit 5 test suite:

```cmd
.\mvnw.cmd test
```

All 30+ tests cover:
- Mock Salesforce query and delta simulation
- Full and incremental synchronization pipelines
- Audit tracking preservation
- Search, sorting, and pagination
- CSV and JSON data export
- Headless CLI runner execution
- JWT registration, login, and authorization filters

---

## Project Structure

```
SF Sync/
├── pom.xml                                 # Maven dependencies & build configuration
├── mvnw / mvnw.cmd                         # Maven wrapper executables
├── run-backend-local.bat                   # 1-click launcher: Instant local H2 profile
├── run-backend-postgres.bat                # 1-click launcher: PostgreSQL profile
├── run-sync.bat                            # 1-click launcher: Headless sync CLI
├── src/
│   ├── main/
│   │   ├── java/com/salesforce/sync/
│   │   │   ├── SalesforceSyncApplication.java
│   │   │   ├── cli/
│   │   │   │   └── SalesforceCliRunner.java      # Headless CLI sync engine
│   │   │   ├── config/
│   │   │   │   ├── OpenApiConfig.java            # Swagger / OpenAPI documentation config
│   │   │   │   ├── SecurityConfig.java           # Spring Security & JWT Filter config
│   │   │   │   └── WebMvcConfig.java             # Static resource handling
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java           # Salesforce OAuth & connection
│   │   │   │   ├── DataController.java           # Data explorer & record creation
│   │   │   │   ├── ExportController.java         # CSV & JSON export
│   │   │   │   ├── LookupController.java         # Dropdown relational lookups
│   │   │   │   ├── MockController.java           # Mock simulator endpoints
│   │   │   │   ├── SchemaController.java         # Salesforce schema introspection
│   │   │   │   ├── SyncController.java           # Sync orchestration & audit
│   │   │   │   └── UserAuthController.java       # User Register/Login JWT
│   │   │   ├── model/
│   │   │   │   ├── dto/                          # Auth request/response DTOs
│   │   │   │   └── entity/                       # JPA entities (Account, Contact, etc.)
│   │   │   ├── repository/                       # Spring Data JPA repositories
│   │   │   ├── security/                         # JWT service & auth filter
│   │   │   └── service/
│   │   │       ├── MockSalesforceService.java    # Realistic in-memory Salesforce sandbox
│   │   │       ├── SalesforceClientService.java  # HTTP client for Salesforce REST APIs
│   │   │       └── SalesforceSyncService.java    # Core sync pipeline & incremental logic
│   │   └── resources/
│   │       ├── application.yml                   # Default PostgreSQL configuration
│   │       └── application-local.yml             # Local H2 in-memory profile
│   └── test/
│       └── java/com/salesforce/sync/             # JUnit 5 test suite
```
