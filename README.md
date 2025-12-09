# Stack Overflow Java Analysis System

A Spring Boot application for analyzing Java trends, topic co-occurrence, and common pitfalls using Stack Overflow data.

## 🛠 Prerequisites

*   **Java 17+**
*   **PostgreSQL 12+**
*   **Maven** (Wrapper included)

## 🚀 Deployment & Setup

### 1. Database Setup
Create a PostgreSQL database named `stackoverflow_analysis`.

Configure `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/stackoverflow_analysis
spring.datasource.username=your_username
spring.datasource.password=your_password

# Use 'update' to keep data on restart
spring.jpa.hibernate.ddl-auto=update
```

2. **Data Initialization (First Run Only)**

First run the test code to validate and collect the data; once verified, insert the data into the database.

3. **Run the Application**

Open a terminal in the project root:

Windows:

```
.\mvnw.cmd spring-boot:run
```

Mac/Linux:

```
./mvnw spring-boot:run
```

4. Access
Visit http://localhost:8080 in your browser.
