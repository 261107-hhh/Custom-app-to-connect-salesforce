package com.salesforce.sync.cli;

import com.salesforce.sync.repository.SyncHistoryRepository;
import com.salesforce.sync.repository.SyncStateRepository;
import com.salesforce.sync.service.SalesforceClientService;
import com.salesforce.sync.service.SalesforceSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SalesforceCliRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SalesforceCliRunner.class);

    private final SalesforceClientService sfClient;
    private final SalesforceSyncService syncService;
    private final ApplicationContext applicationContext;

    public SalesforceCliRunner(SalesforceClientService sfClient,
                               SalesforceSyncService syncService,
                               ApplicationContext applicationContext) {
        this.sfClient = sfClient;
        this.syncService = syncService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) throws Exception {
        boolean cliMode = false;
        List<String> objects = new ArrayList<>(List.of("Account", "Contact", "Opportunity", "Lead"));
        String mode = "incremental";

        for (String arg : args) {
            if (arg.equalsIgnoreCase("--sync") || arg.equalsIgnoreCase("--cli")) {
                cliMode = true;
            } else if (arg.startsWith("--objects=")) {
                cliMode = true;
                String raw = arg.substring("--objects=".length());
                objects = Arrays.stream(raw.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            } else if (arg.startsWith("--mode=")) {
                cliMode = true;
                mode = arg.substring("--mode=".length()).trim().toLowerCase();
            } else if (arg.equalsIgnoreCase("--full")) {
                cliMode = true;
                mode = "full";
            } else if (arg.equalsIgnoreCase("--incremental")) {
                cliMode = true;
                mode = "incremental";
            } else if (arg.equalsIgnoreCase("--help") || arg.equalsIgnoreCase("-h")) {
                printHelp();
                return;
            }
        }

        if (!cliMode) {
            return;
        }

        System.out.println("=======================================================");
        System.out.println("   Salesforce Data Integration Platform - Headless CLI");
        System.out.println("=======================================================");
        System.out.println("Mode:    " + mode.toUpperCase());
        System.out.println("Objects: " + String.join(", ", objects));
        System.out.println("-------------------------------------------------------");

        // 1. Establish connection if not already connected
        if (!sfClient.isConnected()) {
            String sfUser = System.getenv("SF_USERNAME");
            String sfPass = System.getenv("SF_PASSWORD");
            String sfToken = System.getenv("SF_SECURITY_TOKEN");
            String sfClientId = System.getenv("SF_CLIENT_ID");
            String sfClientSecret = System.getenv("SF_CLIENT_SECRET");
            String sfInstanceUrl = System.getenv("SF_INSTANCE_URL");

            if (sfInstanceUrl != null && sfClientId != null && sfClientSecret != null) {
                System.out.println("Connecting via External Client App (OAuth Client Credentials)...");
                sfClient.connect(Map.of(
                        "mode", "eca",
                        "instanceUrl", sfInstanceUrl,
                        "clientId", sfClientId,
                        "clientSecret", sfClientSecret
                ));
            } else if (sfUser != null && sfPass != null) {
                System.out.println("Connecting via Username & Password credentials...");
                sfClient.connect(Map.of(
                        "mode", "password",
                        "username", sfUser,
                        "password", sfPass,
                        "securityToken", sfToken != null ? sfToken : ""
                ));
            } else {
                System.out.println("No live Salesforce credentials found in environment. Connecting to Mock Sandbox...");
                sfClient.connect(Map.of("mode", "mock"));
            }
        }

        System.out.println("Connected: " + (sfClient.isMock() ? "Mock Sandbox" : sfClient.getInstanceUrl()));
        System.out.println("Starting synchronization...\n");

        int totalFetched = 0;
        int totalErrors = 0;

        for (String objectName : objects) {
            long start = System.currentTimeMillis();
            try {
                int count = syncService.syncSingleObject(objectName, mode, Map.of());
                long duration = System.currentTimeMillis() - start;
                totalFetched += count;
                System.out.printf("  [SUCCESS] %-12s : %4d records synced (%d ms)%n", objectName, count, duration);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - start;
                totalErrors++;
                System.err.printf("  [ERROR]   %-12s : Failed (%d ms) - %s%n", objectName, duration, e.getMessage());
            }
        }

        System.out.println("\n-------------------------------------------------------");
        System.out.printf("Sync Finished: %d records processed, %d errors%n", totalFetched, totalErrors);
        System.out.println("=======================================================");

        // If explicitly invoked as a one-shot CLI job, exit cleanly
        String webAppType = System.getProperty("spring.main.web-application-type");
        if ("none".equalsIgnoreCase(webAppType) || Arrays.asList(args).contains("--exit")) {
            int exitCode = totalErrors == 0 ? 0 : 1;
            SpringApplication.exit(applicationContext, () -> exitCode);
            System.exit(exitCode);
        }
    }

    private void printHelp() {
        System.out.println("""
            Salesforce Sync Headless CLI
            
            Usage:
              mvnw spring-boot:run -Dspring-boot.run.arguments="--sync [options]"
              or
              run-sync.bat [options]
            
            Options:
              --sync              Enable CLI headless execution
              --objects=<list>    Comma-separated object list (default: Account,Contact,Opportunity,Lead)
              --mode=<mode>       Sync mode: 'full' or 'incremental' (default: incremental)
              --full              Shorthand for --mode=full
              --incremental       Shorthand for --mode=incremental
              --exit              Exit JVM immediately upon completion
              --help, -h          Display this help message
            """);
    }
}
