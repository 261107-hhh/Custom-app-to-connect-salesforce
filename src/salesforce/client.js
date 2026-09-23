import jsforce from 'jsforce';
import zlib from 'zlib';
import localDb from '../db/database.js';
import mockSalesforce from '../mock/mockSalesforce.js';

class SalesforceClient {
  constructor() {
    this.conn = null;
    this.isMock = true;
    this.connectionInfo = {
      connected: false,
      mode: 'mock',
      username: '',
      instanceUrl: '',
      loginUrl: 'https://login.salesforce.com',
      lastConnectedAt: null
    };

    this.restoreConnectionFromDb();
  }

  restoreConnectionFromDb() {
    try {
      const savedConfig = localDb.getConfig('sf_connection');
      if (savedConfig) {
        this.connectionInfo = { ...this.connectionInfo, ...savedConfig };
        this.isMock = savedConfig.mode === 'mock';
      }
    } catch (err) {
      console.warn('Could not restore connection from DB:', err.message);
    }
  }

  async connect(credentials = {}) {
    const {
      mode = 'mock',
      loginUrl = 'https://login.salesforce.com',
      username,
      password,
      securityToken = '',
      clientId,
      clientSecret,
      accessToken,
      instanceUrl
    } = credentials;

    // 1. Mock Sandbox Mode
    if (mode === 'mock') {
      this.isMock = true;
      this.conn = null;
      this.connectionInfo = {
        connected: true,
        mode: 'mock',
        username: 'developer@sandbox.mock',
        instanceUrl: 'https://mock.salesforce.local',
        loginUrl,
        lastConnectedAt: new Date().toISOString()
      };
      localDb.setConfig('sf_connection', this.connectionInfo);
      return { success: true, mode: 'mock', message: 'Connected to Salesforce Mock Environment' };
    }

    // 2. External Client App (OAuth 2.0 Client Credentials Flow)
    if (mode === 'eca' || mode === 'client_credentials') {
      if (!instanceUrl || !clientId || !clientSecret) {
        throw new Error('My Domain URL, Consumer Key (Client ID), and Consumer Secret are required for External Client App authentication.');
      }

      let cleanInstanceUrl = instanceUrl.trim().replace(/\/$/, '');
      if (!/^https?:\/\//i.test(cleanInstanceUrl)) {
        cleanInstanceUrl = `https://${cleanInstanceUrl}`;
      }
      if (cleanInstanceUrl.includes('.lightning.force.com')) {
        cleanInstanceUrl = cleanInstanceUrl.replace('.lightning.force.com', '.my.salesforce.com');
      }

      const tokenEndpoint = `${cleanInstanceUrl}/services/oauth2/token`;

      const bodyParams = new URLSearchParams({
        grant_type: 'client_credentials',
        client_id: clientId.trim(),
        client_secret: clientSecret.trim()
      });

      const tokenRes = await fetch(tokenEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Accept': 'application/json',
          'Accept-Encoding': 'gzip, deflate, identity'
        },
        body: bodyParams.toString()
      });

      const arrayBuf = await tokenRes.arrayBuffer();
      const buffer = Buffer.from(arrayBuf);
      let responseText = '';

      if (buffer.length >= 2 && buffer[0] === 0x1f && buffer[1] === 0x8b) {
        try {
          responseText = zlib.gunzipSync(buffer).toString('utf-8');
        } catch (zlibErr) {
          throw new Error(`Failed to decompress Salesforce response: ${zlibErr.message}`);
        }
      } else {
        responseText = buffer.toString('utf-8');
      }

      let tokenData;
      try {
        tokenData = JSON.parse(responseText);
      } catch {
        throw new Error(`Invalid response from Salesforce (${tokenRes.status} ${tokenRes.statusText}): ${responseText.slice(0, 200)}`);
      }

      if (!tokenRes.ok || !tokenData.access_token) {
        const errorDetail = tokenData.error_description 
          ? `${tokenData.error}: ${tokenData.error_description}` 
          : (tokenData.error || tokenData.message || 'External Client App authentication failed.');
        throw new Error(errorDetail);
      }

      const effectiveInstanceUrl = tokenData.instance_url || cleanInstanceUrl;

      const conn = new jsforce.Connection({
        instanceUrl: effectiveInstanceUrl,
        accessToken: tokenData.access_token
      });

      this.conn = conn;
      this.isMock = false;
      this.connectionInfo = {
        connected: true,
        mode: 'eca',
        username: 'External Client App (Integration User)',
        instanceUrl: effectiveInstanceUrl,
        loginUrl: cleanInstanceUrl,
        clientId,
        lastConnectedAt: new Date().toISOString()
      };

      localDb.setConfig('sf_connection', {
        connected: true,
        mode: 'eca',
        username: this.connectionInfo.username,
        instanceUrl: effectiveInstanceUrl,
        loginUrl: cleanInstanceUrl,
        clientId,
        lastConnectedAt: this.connectionInfo.lastConnectedAt
      });

      // Reset sync timestamps so incremental sync starts fresh for new org
      localDb.resetAllSyncStates();

      return {
        success: true,
        mode: 'eca',
        instanceUrl: effectiveInstanceUrl,
        message: 'Successfully authenticated via External Client App (Client Credentials Flow)!'
      };
    }

    // 3. Direct Username + Password + Security Token Flow
    if (mode === 'password') {
      if (!username || !password) {
        throw new Error('Username and Password are required for Direct Password authentication.');
      }

      const conn = new jsforce.Connection({
        loginUrl: loginUrl || 'https://login.salesforce.com'
      });

      const fullPassword = securityToken ? `${password}${securityToken}` : password;
      const userInfo = await conn.login(username, fullPassword);

      this.conn = conn;
      this.isMock = false;
      this.connectionInfo = {
        connected: true,
        mode: 'password',
        username,
        userId: userInfo.id,
        organizationId: userInfo.organizationId,
        instanceUrl: conn.instanceUrl,
        loginUrl,
        lastConnectedAt: new Date().toISOString()
      };

      localDb.setConfig('sf_connection', {
        connected: true,
        mode: 'password',
        username,
        instanceUrl: conn.instanceUrl,
        loginUrl,
        lastConnectedAt: this.connectionInfo.lastConnectedAt
      });

      return {
        success: true,
        mode: 'password',
        userInfo,
        instanceUrl: conn.instanceUrl,
        message: `Successfully connected to Salesforce as ${username}`
      };
    }

    // 4. OAuth 2.0 / Access Token Flow
    if (mode === 'oauth') {
      if (!accessToken || !instanceUrl) {
        throw new Error('Access Token and Instance URL are required for OAuth session connection.');
      }

      const conn = new jsforce.Connection({
        instanceUrl,
        accessToken
      });

      const identity = await conn.identity();

      this.conn = conn;
      this.isMock = false;
      this.connectionInfo = {
        connected: true,
        mode: 'oauth',
        username: identity.username,
        userId: identity.user_id,
        organizationId: identity.organization_id,
        instanceUrl,
        loginUrl,
        lastConnectedAt: new Date().toISOString()
      };

      localDb.setConfig('sf_connection', {
        connected: true,
        mode: 'oauth',
        username: identity.username,
        instanceUrl,
        loginUrl,
        lastConnectedAt: this.connectionInfo.lastConnectedAt
      });

      return {
        success: true,
        mode: 'oauth',
        identity,
        instanceUrl,
        message: `Successfully connected via OAuth as ${identity.username}`
      };
    }

    throw new Error(`Unsupported authentication mode: ${mode}`);
  }

  async disconnect() {
    if (this.conn && !this.isMock) {
      try {
        await this.conn.logout();
      } catch {
        // Ignore logout errors
      }
    }
    this.conn = null;
    this.connectionInfo.connected = false;
    localDb.setConfig('sf_connection', { ...this.connectionInfo, connected: false });
    return { success: true, message: 'Disconnected from Salesforce' };
  }

  getStatus() {
    return {
      ...this.connectionInfo,
      isMock: this.isMock
    };
  }

  async describeGlobal() {
    if (this.isMock) {
      return mockSalesforce.describeGlobal();
    }
    if (!this.conn) {
      throw new Error('Not connected to Salesforce. Please connect first.');
    }
    return await this.conn.describeGlobal();
  }

  async describeObject(objectName) {
    if (this.isMock) {
      return mockSalesforce.describeObject(objectName);
    }
    if (!this.conn) {
      throw new Error('Not connected to Salesforce. Please connect first.');
    }
    const apiVersion = this.conn.version || '60.0';
    console.log(`[SF Describe] API: ${this.conn.instanceUrl}/services/data/v${apiVersion}/sobjects/${objectName}/describe`);
    return await this.conn.describe(objectName);
  }

  async query(soql) {
    if (this.isMock) {
      return mockSalesforce.query(soql);
    }
    if (!this.conn) {
      throw new Error('Not connected to Salesforce. Please connect first.');
    }

    const apiVersion = this.conn.version || '60.0';
    const apiEndpoint = `${this.conn.instanceUrl}/services/data/v${apiVersion}/query?q=...`;
    console.log(`[SF Query] API Endpoint: ${apiEndpoint}`);
    console.log(`[SF Query] Executing SOQL (${soql.length} chars): ${soql.substring(0, 150)}...`);

    try {
      // JSforce v3: use autoFetch and maxFetch as chained methods, then await
      const result = await this.conn.query(soql).autoFetch(true).maxFetch(50000);

      // JSforce v3 can return result in multiple shapes depending on responseTarget
      // Default: { totalSize, done, records: [...] }
      // But sometimes records can be nested differently
      const records = result.records || [];
      const totalSize = result.totalSize ?? records.length;

      console.log(`[SF Query] Success: totalSize=${totalSize}, records.length=${records.length}, done=${result.done}`);

      return {
        totalSize,
        done: result.done ?? true,
        records
      };
    } catch (err) {
      console.error(`[SF Query] ERROR: ${err.message}`);
      if (err.errorCode) {
        console.error(`[SF Query] Salesforce errorCode: ${err.errorCode}`);
      }
      if (err.fields) {
        console.error(`[SF Query] Problem fields: ${JSON.stringify(err.fields)}`);
      }
      throw err;
    }
  }
}

export const sfClient = new SalesforceClient();
export default sfClient;
