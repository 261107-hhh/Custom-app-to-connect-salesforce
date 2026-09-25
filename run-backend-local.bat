@echo off
title Salesforce Sync - Java Spring Boot Backend (Instant Local Profile)
echo ===================================================
echo Starting Salesforce Sync Backend (Local Profile)
echo Swagger UI:   http://localhost:8080/swagger-ui.html
echo H2 Console:   http://localhost:8080/h2-console
echo ===================================================
call .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
pause
