package com.salesforce.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.sync.model.entity.SyncConfigEntity;
import com.salesforce.sync.repository.SyncConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class SalesforceClientService {

    private static final Logger log = LoggerFactory.getLogger(SalesforceClientService.class);

    private final ObjectMapper objectMapper;
    private final SyncConfigRepository configRepository;
    private final MockSalesforceService mockSalesforceService;
    private final HttpClient httpClient;

    private boolean connected = false;
    private boolean isMock = false;
    private String mode = "mock";
    private String accessToken;
    private String instanceUrl;
    private String username;
    private String apiVersion = "60.0";

    public SalesforceClientService(ObjectMapper objectMapper,
                                   SyncConfigRepository configRepository,
                                   MockSalesforceService mockSalesforceService) {
        this.objectMapper = objectMapper;
        this.configRepository = configRepository;
        this.mockSalesforceService = mockSalesforceService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            Optional<SyncConfigEntity> cfg = configRepository.findById("sf_connection");
            if (cfg.isPresent()) {
                JsonNode node = objectMapper.readTree(cfg.get().getValue());
                this.mode = node.path("mode").asText("mock");
                this.username = node.path("username").asText("developer@sandbox.mock");
                this.instanceUrl = node.path("instanceUrl").asText("https://mock.salesforce.local");
                this.connected = true;
                this.isMock = "mock".equalsIgnoreCase(this.mode);
                if (this.isMock) {
                    this.accessToken = "mock-token-xyz";
                }
            } else {
                // Default to mock mode out-of-the-box
                this.isMock = true;
                this.connected = true;
                this.mode = "mock";
                this.username = "developer@sandbox.mock";
                this.instanceUrl = "https://mock.salesforce.local";
                this.accessToken = "mock-token-xyz";
            }
        } catch (Exception e) {
            log.warn("Could not initialize Salesforce connection: {}", e.getMessage());
            this.isMock = true;
            this.connected = true;
            this.mode = "mock";
            this.username = "developer@sandbox.mock";
            this.instanceUrl = "https://mock.salesforce.local";
            this.accessToken = "mock-token-xyz";
        }
    }

    public synchronized Map<String, Object> connect(Map<String, Object> credentials) throws Exception {
        String reqMode = (String) credentials.getOrDefault("mode", "mock");

        if ("mock".equalsIgnoreCase(reqMode)) {
            this.isMock = true;
            this.connected = true;
            this.mode = "mock";
            this.username = "developer@sandbox.mock";
            this.instanceUrl = "https://mock.salesforce.local";
            this.accessToken = "mock-token-xyz";

            saveConnectionConfig();
            return Map.of("success", true, "mode", "mock", "message", "Connected to Salesforce Mock Sandbox");
        }

        if ("eca".equalsIgnoreCase(reqMode)) {
            String instUrl = (String) credentials.get("instanceUrl");
            String clientId = (String) credentials.get("clientId");
            String clientSecret = (String) credentials.get("clientSecret");

            if (instUrl == null || clientId == null || clientSecret == null) {
                throw new IllegalArgumentException("Instance URL, Client ID, and Client Secret are required for External Client App.");
            }

            instUrl = instUrl.replaceAll("/+$", "");
            String tokenEndpoint = instUrl + "/services/oauth2/token";
            log.info("[SF Auth] Requesting token via ECA Client Credentials: {}", tokenEndpoint);

            String formBody = "grant_type=client_credentials" +
                    "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                    "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Salesforce OAuth failed (" + response.statusCode() + "): " + response.body());
            }

            JsonNode tokenJson = objectMapper.readTree(response.body());
            this.accessToken = tokenJson.path("access_token").asText();
            this.instanceUrl = tokenJson.has("instance_url") ? tokenJson.path("instance_url").asText() : instUrl;
            this.connected = true;
            this.isMock = false;
            this.mode = "eca";
            this.username = "External Client App (Integration User)";

            saveConnectionConfig();
            return Map.of("success", true, "mode", "eca", "instanceUrl", this.instanceUrl,
                    "message", "Successfully authenticated via External Client App (Client Credentials)!");
        }

        if ("password".equalsIgnoreCase(reqMode)) {
            String loginUrl = (String) credentials.getOrDefault("loginUrl", "https://login.salesforce.com");
            String user = (String) credentials.get("username");
            String pass = (String) credentials.get("password");
            String token = (String) credentials.getOrDefault("securityToken", "");

            if (user == null || pass == null) {
                throw new IllegalArgumentException("Username and Password are required.");
            }

            loginUrl = loginUrl.replaceAll("/+$", "");
            String tokenEndpoint = loginUrl + "/services/oauth2/token";

            String formBody = "grant_type=password" +
                    "&client_id=SalesforceLocalSyncApp" +
                    "&client_secret=" +
                    "&username=" + URLEncoder.encode(user, StandardCharsets.UTF_8) +
                    "&password=" + URLEncoder.encode(pass + token, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("Salesforce Password Auth failed (" + response.statusCode() + "): " + response.body());
            }

            JsonNode tokenJson = objectMapper.readTree(response.body());
            this.accessToken = tokenJson.path("access_token").asText();
            this.instanceUrl = tokenJson.path("instance_url").asText();
            this.connected = true;
            this.isMock = false;
            this.mode = "password";
            this.username = user;

            saveConnectionConfig();
            return Map.of("success", true, "mode", "password", "username", this.username,
                    "message", "Successfully connected to Salesforce as " + this.username);
        }

        if ("oauth".equalsIgnoreCase(reqMode)) {
            String instUrl = (String) credentials.get("instanceUrl");
            String token = (String) credentials.get("accessToken");

            if (instUrl == null || token == null) {
                throw new IllegalArgumentException("Instance URL and Access Token are required for OAuth session connection.");
            }

            instUrl = instUrl.replaceAll("/+$", "");
            this.accessToken = token;
            this.instanceUrl = instUrl;
            this.connected = true;
            this.isMock = false;
            this.mode = "oauth";
            this.username = (String) credentials.getOrDefault("username", "OAuth User");

            saveConnectionConfig();
            return Map.of("success", true, "mode", "oauth", "instanceUrl", this.instanceUrl,
                    "message", "Successfully connected via OAuth session token!");
        }

        throw new IllegalArgumentException("Unsupported authentication mode: " + reqMode);
    }

    public synchronized void disconnect() {
        this.connected = false;
        this.accessToken = null;
        this.isMock = false;
        configRepository.deleteById("sf_connection");
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connected", this.connected);
        status.put("isMock", this.isMock);
        status.put("mode", this.mode);
        status.put("username", this.username);
        status.put("instanceUrl", this.instanceUrl);
        return status;
    }

    public JsonNode describeGlobal() throws Exception {
        if (this.isMock) {
            return mockSalesforceService.describeGlobal();
        }
        ensureConnected();

        String endpoint = this.instanceUrl + "/services/data/v" + this.apiVersion + "/sobjects";
        log.info("[SF Describe Global] GET {}", endpoint);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + this.accessToken)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(25))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Salesforce describeGlobal failed: " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    public JsonNode describeObject(String objectName) throws Exception {
        if (this.isMock) {
            return mockSalesforceService.describeObject(objectName);
        }
        ensureConnected();

        String endpoint = this.instanceUrl + "/services/data/v" + this.apiVersion + "/sobjects/" + objectName + "/describe";
        log.info("[SF Describe] GET {}", endpoint);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + this.accessToken)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(25))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Salesforce describe failed: " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    public JsonNode query(String soql) throws Exception {
        if (this.isMock) {
            return mockSalesforceService.query(soql);
        }
        ensureConnected();

        String encodedSoql = URLEncoder.encode(soql, StandardCharsets.UTF_8);
        String endpoint = this.instanceUrl + "/services/data/v" + this.apiVersion + "/query?q=" + encodedSoql;
        log.info("[SF Query] GET {}", endpoint);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + this.accessToken)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(35))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Salesforce SOQL query failed: " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    public Map<String, Object> createRecord(String objectName, Map<String, Object> fields) throws Exception {
        if (this.isMock) {
            return mockSalesforceService.createRecord(objectName, fields);
        }
        ensureConnected();

        String endpoint = this.instanceUrl + "/services/data/v" + this.apiVersion + "/sobjects/" + objectName;
        log.info("[SF Create] POST {}", endpoint);

        String payloadJson = objectMapper.writeValueAsString(fields);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + this.accessToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(25))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Salesforce Create Record failed: " + response.body());
        }

        JsonNode resJson = objectMapper.readTree(response.body());
        if (!resJson.path("success").asBoolean(false)) {
            throw new RuntimeException("Salesforce rejected record: " + response.body());
        }

        String newId = resJson.path("id").asText();
        log.info("[SF Create] Record created successfully! ID: {}", newId);
        return Map.of("success", true, "id", newId, "objectName", objectName);
    }

    public List<Map<String, Object>> getCreatableFields(String objectName) throws Exception {
        JsonNode describe = describeObject(objectName);
        List<Map<String, Object>> creatable = new ArrayList<>();

        JsonNode fieldsNode = describe.path("fields");
        if (fieldsNode.isArray()) {
            for (JsonNode f : fieldsNode) {
                if (f.path("createable").asBoolean(false) && !f.path("deprecatedAndHidden").asBoolean(false)) {
                    Map<String, Object> fieldMap = new HashMap<>();
                    fieldMap.put("name", f.path("name").asText());
                    fieldMap.put("label", f.path("label").asText());
                    fieldMap.put("type", f.path("type").asText());
                    fieldMap.put("required", !f.path("nillable").asBoolean(true) && !f.path("defaultedOnCreate").asBoolean(false));

                    List<String> refTo = new ArrayList<>();
                    if (f.has("referenceTo") && f.path("referenceTo").isArray()) {
                        f.path("referenceTo").forEach(r -> refTo.add(r.asText()));
                    }
                    fieldMap.put("referenceTo", refTo);

                    List<Map<String, String>> picklists = new ArrayList<>();
                    if (f.has("picklistValues") && f.path("picklistValues").isArray()) {
                        for (JsonNode p : f.path("picklistValues")) {
                            if (p.path("active").asBoolean(true)) {
                                picklists.add(Map.of("label", p.path("label").asText(), "value", p.path("value").asText()));
                            }
                        }
                    }
                    fieldMap.put("picklistValues", picklists);

                    creatable.add(fieldMap);
                }
            }
        }
        return creatable;
    }

    private void ensureConnected() {
        if (!this.connected || this.accessToken == null) {
            throw new IllegalStateException("Not connected to Salesforce. Please connect first via /api/auth/connect.");
        }
    }

    private void saveConnectionConfig() {
        try {
            Map<String, Object> cfg = new HashMap<>();
            cfg.put("mode", this.mode);
            cfg.put("username", this.username);
            cfg.put("instanceUrl", this.instanceUrl);
            configRepository.save(new SyncConfigEntity("sf_connection", objectMapper.writeValueAsString(cfg)));
        } catch (Exception e) {
            log.warn("Could not save connection state: {}", e.getMessage());
        }
    }

    public boolean isConnected() { return connected; }
    public boolean isMock() { return isMock; }
    public String getInstanceUrl() { return instanceUrl; }
}
