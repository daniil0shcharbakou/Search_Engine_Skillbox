# Search_Engine_Skillbox

A simple web search engine built with Java, Spring Boot, JPA, MySQL, Jsoup, and Lucene Morphology.

## Project Overview

This project crawls configured websites, parses page content, extracts and indexes Russian lemmas, and provides a REST API for searching and retrieving statistics on the indexed data.

Key features:

* Multithreaded site crawling with Jsoup
* HTML cleanup and Russian lemmatization (Lucene Morphology)
* Relational storage (MySQL) via Spring Data JPA
* REST API for indexing control, full-text search, and statistics
* Simple Thymeleaf-based frontend

## Technologies Used

* Java 17
* Spring Boot 3.1
* Spring Data JPA
* MySQL 8
* Jsoup 1.15.3
* Apache Lucene Morphology 7.7.3
* Thymeleaf

## Prerequisites

* Java 17+ installed
* Maven 3.6+
* MySQL Server 8.x

## Project Structure


search-engine/
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/skillbox/searchengine/
│   │   │   ├── Application.java
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── model/
│   │   │   ├── repository/
│   │   │   ├── service/
│   │   │   └── util/
│   │   └── resources/
│   │       ├── application.yaml
│   │       └── templates/
├── static/ (optional front-end assets)
└── test/


## Configuration

Edit `src/main/resources/application.yaml` to set database credentials, connection URL, and sites to crawl:

yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/search_engine?useSSL=false&serverTimezone=UTC
    username: root
    password: password
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
app:
  sites:
    - url: "https://example.com"
      name: "example"
    - url: "https://example.org"
      name: "example-org"
  user-agent: "SearchEngineBot/1.0"


## Database Setup

1. Create the database:

   sql
   CREATE DATABASE search_engine CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   
2. (Optional) Create a dedicated user:

   sql
   CREATE USER 'search_user'@'localhost' IDENTIFIED BY 's3cr3t';
   GRANT ALL ON search_engine.* TO 'search_user'@'localhost';
   
3. Ensure credentials in `application.yaml` match.

With `spring.jpa.hibernate.ddl-auto=update`, tables are created automatically.

## Running the Application

bash
mvn clean package
java -jar target/search-engine-1.0-SNAPSHOT.jar


Open `http://localhost:8080/` in your browser.

## REST API Endpoints

* **GET** `/api/startIndexing` — Start crawling and indexing configured sites.
* **GET** `/api/search?query={term}&offset={n}&limit={m}` — Full-text search. Returns JSON:

  json
  {
    "count": 3,
    "results": [
      {"uri":"https://...","title":"...","snippet":"...","relevance":5.0},
      ...
    ]
  }
  
* **GET** `/api/statistics` — Retrieve indexing statistics. Returns JSON:

  json
  {
    "totalSites":2,
    "totalPages":10,
    "totalLemmas":1234,
    "details":[{...},...]  
  }
  

## Frontend

A simple Thymeleaf page (`index.html`) provides a search form and button to load statistics (logged to console).

## Evaluation Criteria

Per the course requirements, the project includes:

* Correct JPA entity design
* Full-text indexing and search logic
* Parallel crawling implementation
* REST API coverage
* Automatic schema generation
* Clean code and project structure
* Basic frontend interface

## Author

Developed by \[Your Name].

## License

MIT License
