// In-memory Mock Salesforce data generator for local testing and simulation

const generateSfId = (prefix, index) => {
  const padded = String(index).padStart(12, '0');
  return `${prefix}xx${padded}AAA`;
};

const initialAccounts = [
  {
    Id: generateSfId('001', 1),
    Name: 'Acme Corporation',
    Type: 'Customer - Direct',
    Industry: 'Manufacturing',
    AnnualRevenue: 50000000,
    Phone: '(555) 123-4567',
    BillingCity: 'San Francisco',
    BillingCountry: 'USA',
    CreatedDate: '2026-01-10T08:30:00.000Z',
    LastModifiedDate: '2026-08-15T12:00:00.000Z',
    SystemModstamp: '2026-08-15T12:00:00.000Z'
  },
  {
    Id: generateSfId('001', 2),
    Name: 'Global Cloud Innovations',
    Type: 'Customer - Channel',
    Industry: 'Technology',
    AnnualRevenue: 120000000,
    Phone: '(555) 987-6543',
    BillingCity: 'Austin',
    BillingCountry: 'USA',
    CreatedDate: '2026-02-14T09:15:00.000Z',
    LastModifiedDate: '2026-09-01T14:20:00.000Z',
    SystemModstamp: '2026-09-01T14:20:00.000Z'
  },
  {
    Id: generateSfId('001', 3),
    Name: 'Apex Health Systems',
    Type: 'Customer - Direct',
    Industry: 'Healthcare',
    AnnualRevenue: 85000000,
    Phone: '(555) 456-7890',
    BillingCity: 'Boston',
    BillingCountry: 'USA',
    CreatedDate: '2026-03-01T10:00:00.000Z',
    LastModifiedDate: '2026-09-10T16:45:00.000Z',
    SystemModstamp: '2026-09-10T16:45:00.000Z'
  },
  {
    Id: generateSfId('001', 4),
    Name: 'BlueSky Logistics',
    Type: 'Prospect',
    Industry: 'Transportation',
    AnnualRevenue: 24000000,
    Phone: '(555) 234-5678',
    BillingCity: 'Chicago',
    BillingCountry: 'USA',
    CreatedDate: '2026-04-12T11:30:00.000Z',
    LastModifiedDate: '2026-09-18T09:10:00.000Z',
    SystemModstamp: '2026-09-18T09:10:00.000Z'
  },
  {
    Id: generateSfId('001', 5),
    Name: 'Starlight Financial Group',
    Type: 'Customer - Direct',
    Industry: 'Finance',
    AnnualRevenue: 340000000,
    Phone: '(555) 876-5432',
    BillingCity: 'New York',
    BillingCountry: 'USA',
    CreatedDate: '2026-05-20T14:00:00.000Z',
    LastModifiedDate: '2026-09-22T08:00:00.000Z',
    SystemModstamp: '2026-09-22T08:00:00.000Z'
  }
];

const initialContacts = [
  {
    Id: generateSfId('003', 1),
    FirstName: 'Sarah',
    LastName: 'Connor',
    Name: 'Sarah Connor',
    Email: 's.connor@acme.com',
    Phone: '(555) 123-4501',
    Title: 'VP of Technology',
    AccountId: generateSfId('001', 1),
    CreatedDate: '2026-01-11T09:00:00.000Z',
    LastModifiedDate: '2026-08-16T10:00:00.000Z',
    SystemModstamp: '2026-08-16T10:00:00.000Z'
  },
  {
    Id: generateSfId('003', 2),
    FirstName: 'David',
    LastName: 'Kim',
    Name: 'David Kim',
    Email: 'dkim@globalcloud.io',
    Phone: '(555) 987-6502',
    Title: 'Chief Information Officer',
    AccountId: generateSfId('001', 2),
    CreatedDate: '2026-02-15T11:00:00.000Z',
    LastModifiedDate: '2026-09-02T11:00:00.000Z',
    SystemModstamp: '2026-09-02T11:00:00.000Z'
  },
  {
    Id: generateSfId('003', 3),
    FirstName: 'Elena',
    LastName: 'Rostova',
    Name: 'Elena Rostova',
    Email: 'erostova@apexhealth.org',
    Phone: '(555) 456-7803',
    Title: 'Director of Operations',
    AccountId: generateSfId('001', 3),
    CreatedDate: '2026-03-05T13:20:00.000Z',
    LastModifiedDate: '2026-09-11T14:30:00.000Z',
    SystemModstamp: '2026-09-11T14:30:00.000Z'
  },
  {
    Id: generateSfId('003', 4),
    FirstName: 'Marcus',
    LastName: 'Vance',
    Name: 'Marcus Vance',
    Email: 'mvance@blueskylogistics.com',
    Phone: '(555) 234-5604',
    Title: 'Head of Supply Chain',
    AccountId: generateSfId('001', 4),
    CreatedDate: '2026-04-15T15:45:00.000Z',
    LastModifiedDate: '2026-09-18T10:00:00.000Z',
    SystemModstamp: '2026-09-18T10:00:00.000Z'
  }
];

const initialOpportunities = [
  {
    Id: generateSfId('006', 1),
    Name: 'Acme - Enterprise Cloud Migration',
    StageName: 'Closed Won',
    Amount: 250000,
    Probability: 100,
    CloseDate: '2026-08-30',
    AccountId: generateSfId('001', 1),
    CreatedDate: '2026-06-01T10:00:00.000Z',
    LastModifiedDate: '2026-08-30T17:00:00.000Z',
    SystemModstamp: '2026-08-30T17:00:00.000Z'
  },
  {
    Id: generateSfId('006', 2),
    Name: 'Global Cloud - AI Analytics License',
    StageName: 'Negotiation/Review',
    Amount: 480000,
    Probability: 80,
    CloseDate: '2026-10-15',
    AccountId: generateSfId('001', 2),
    CreatedDate: '2026-07-15T11:30:00.000Z',
    LastModifiedDate: '2026-09-20T15:00:00.000Z',
    SystemModstamp: '2026-09-20T15:00:00.000Z'
  },
  {
    Id: generateSfId('006', 3),
    Name: 'Apex Health - Compliance Security Suite',
    StageName: 'Proposal/Price Quote',
    Amount: 175000,
    Probability: 60,
    CloseDate: '2026-11-01',
    AccountId: generateSfId('001', 3),
    CreatedDate: '2026-08-10T14:00:00.000Z',
    LastModifiedDate: '2026-09-12T16:00:00.000Z',
    SystemModstamp: '2026-09-12T16:00:00.000Z'
  }
];

const initialLeads = [
  {
    Id: generateSfId('00Q', 1),
    FirstName: 'Julian',
    LastName: 'Archer',
    Name: 'Julian Archer',
    Company: 'Quantum Dynamics',
    Email: 'jarcher@quantumdyn.com',
    Phone: '(555) 777-1122',
    Status: 'Open - Not Contacted',
    LeadSource: 'Web',
    CreatedDate: '2026-09-15T09:00:00.000Z',
    LastModifiedDate: '2026-09-15T09:00:00.000Z',
    SystemModstamp: '2026-09-15T09:00:00.000Z'
  },
  {
    Id: generateSfId('00Q', 2),
    FirstName: 'Olivia',
    LastName: 'Chen',
    Name: 'Olivia Chen',
    Company: 'CyberSecure Lab',
    Email: 'ochen@cyberseclab.io',
    Phone: '(555) 777-3344',
    Status: 'Working - Contacted',
    LeadSource: 'Partner Referral',
    CreatedDate: '2026-09-19T14:30:00.000Z',
    LastModifiedDate: '2026-09-21T11:20:00.000Z',
    SystemModstamp: '2026-09-21T11:20:00.000Z'
  }
];

class MockSalesforceServer {
  constructor() {
    this.data = {
      Account: [...initialAccounts],
      Contact: [...initialContacts],
      Opportunity: [...initialOpportunities],
      Lead: [...initialLeads]
    };
  }

  describeGlobal() {
    return {
      sobjects: [
        { name: 'Account', label: 'Account', queryable: true, custom: false },
        { name: 'Contact', label: 'Contact', queryable: true, custom: false },
        { name: 'Opportunity', label: 'Opportunity', queryable: true, custom: false },
        { name: 'Lead', label: 'Lead', queryable: true, custom: false },
        { name: 'Product2', label: 'Product', queryable: true, custom: false },
        { name: 'Case', label: 'Case', queryable: true, custom: false }
      ]
    };
  }

  describeObject(objectName) {
    const list = this.data[objectName] || [];
    const sample = list[0] || {};
    const fields = Object.keys(sample).map(key => ({
      name: key,
      label: key,
      type: typeof sample[key] === 'number' ? 'double' : 'string'
    }));

    return {
      name: objectName,
      fields: fields.length > 0 ? fields : [
        { name: 'Id', label: 'Id', type: 'id' },
        { name: 'Name', label: 'Name', type: 'string' },
        { name: 'SystemModstamp', label: 'SystemModstamp', type: 'datetime' }
      ]
    };
  }

  query(soql) {
    const match = soql.match(/FROM\s+(\w+)(?:\s+WHERE\s+(.+))?/i);
    if (!match) {
      return { totalSize: 0, done: true, records: [] };
    }

    const objectName = match[1];
    const whereClause = match[2];
    let records = [...(this.data[objectName] || [])];

    if (whereClause) {
      const timeMatch = whereClause.match(/SystemModstamp\s*>\s*([^\s]+)/i);
      if (timeMatch) {
        const threshold = new Date(timeMatch[1].replace(/'/g, ''));
        records = records.filter(r => new Date(r.SystemModstamp) > threshold);
      }
    }

    // Sort by SystemModstamp ASC to simulate Salesforce query behavior
    records.sort((a, b) => new Date(a.SystemModstamp) - new Date(b.SystemModstamp));

    const formattedRecords = records.map(r => ({
      attributes: {
        type: objectName,
        url: `/services/data/v60.0/sobjects/${objectName}/${r.Id}`
      },
      ...r
    }));

    return {
      totalSize: formattedRecords.length,
      done: true,
      records: formattedRecords
    };
  }

  simulateNewOrModifiedRecord(objectName) {
    if (!this.data[objectName]) this.data[objectName] = [];
    // Ensure timestamp is safely in the future relative to existing records
    const now = new Date(Date.now() + 5000).toISOString();
    const count = this.data[objectName].length + 1;
    
    let newRec;
    if (objectName === 'Account') {
      newRec = {
        Id: generateSfId('001', count),
        Name: `Dynamic Simulated Corp ${count}`,
        Type: 'Prospect',
        Industry: 'Software',
        AnnualRevenue: 15000000 + count * 500000,
        Phone: `(555) 999-${String(count).padStart(4, '0')}`,
        BillingCity: 'Seattle',
        BillingCountry: 'USA',
        CreatedDate: now,
        LastModifiedDate: now,
        SystemModstamp: now
      };
    } else if (objectName === 'Contact') {
      newRec = {
        Id: generateSfId('003', count),
        FirstName: `ContactFirst${count}`,
        LastName: `Simulated${count}`,
        Name: `ContactFirst${count} Simulated${count}`,
        Email: `simulated${count}@example.com`,
        Phone: `(555) 888-${String(count).padStart(4, '0')}`,
        Title: 'Software Architect',
        AccountId: generateSfId('001', 1),
        CreatedDate: now,
        LastModifiedDate: now,
        SystemModstamp: now
      };
    } else if (objectName === 'Opportunity') {
      newRec = {
        Id: generateSfId('006', count),
        Name: `Simulated Enterprise Expansion ${count}`,
        StageName: 'Prospecting',
        Amount: 320000,
        Probability: 40,
        CloseDate: '2026-12-31',
        AccountId: generateSfId('001', 1),
        CreatedDate: now,
        LastModifiedDate: now,
        SystemModstamp: now
      };
    } else {
      newRec = {
        Id: generateSfId('00X', count),
        Name: `Simulated ${objectName} ${count}`,
        CreatedDate: now,
        LastModifiedDate: now,
        SystemModstamp: now
      };
    }

    this.data[objectName].push(newRec);
    return newRec;
  }
}

export const mockSalesforce = new MockSalesforceServer();
export default mockSalesforce;
