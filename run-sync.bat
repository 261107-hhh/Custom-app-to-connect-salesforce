@echo off
title Salesforce Sync - Headless CLI
echo ===================================================
echo Running Salesforce Data Sync CLI
echo ===================================================
call .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--sync --exit %*"
