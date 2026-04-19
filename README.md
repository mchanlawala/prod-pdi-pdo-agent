# Production PDI/PDO Agent

**File‑based agent** for tracking actual production execution (PDI/PDO) without a database.  
Processes Excel operation logs, generates PDI/PDO records, creates alerts for scrap/cycle time anomalies, and updates production order remaining quantities.

## Features

- Upload Excel with operation‑level actual data (start, finish, good/scrap, downtime)
- Automatic PDI (Production Data Input) and PDO (Production Data Output) records
- Rule‑based agent:
    - Scrap rate >2% → alert
    - Cycle time >2× planned → alert
    - Auto‑infer missing downtime
- File storage (JSON) – no database required
- REST API for uploading logs, retrieving PDI/PDO, and acknowledging alerts

## Tech Stack

- Java 21
- Spring Boot 3.2.5
- Apache POI (Excel parsing)
- Jackson (JSON serialisation)
- Lombok
- Maven

## Quick Start

### 1. Build

```bash
mvn clean package