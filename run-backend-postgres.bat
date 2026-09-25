@echo off
title Salesforce Sync - Java Spring Boot Backend (PostgreSQL)
echo ===================================================
echo Starting Salesforce Sync Backend (PostgreSQL)
echo Database URL: jdbc:postgresql://localhost:5432/postgres
echo Swagger UI:   http://localhost:8080/swagger-ui.html
echo ===================================================
call .\mvnw.cmd spring-boot:run
pause
