# Поисковый движок (Search Engine) — итоговый проект курса «Java-разработчик»

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

---

# Оглавление

* [Коротко о функционале](#коротко-о-функционале)
* [Ключевые возможности](#ключевые-возможности)
* [Требования](#требования)
* [Быстрый старт (3 шага)](#быстрый-старт-3-шага)

  * [1. Подготовка MySQL](#1-подготовка-mysql)
  * [2. Настройка application.yml](#2-настройка-applicationyml)
  * [3. Сборка и запуск](#3-сборка-и-запуск)
* [Как это работает](#как-работает-коротко)
* [Чек-лист соответствия ТЗ (для защиты)](#чек-лист-соответствия-тз-для-защиты)
* [Лицензия](#лицензия)
* [Контакты / Автор](#контакты--автор)

---

# Коротко о функционале

Проект индексирует сайты и предоставляет поиск по ним. Результаты возвращают `title`, `uri`, `snippet` (plain text, без HTML) и `relevance` (оценка TF-IDF). Веб-часть реализована на Spring (Thymeleaf) — Dashboard, Management, Search. БД — MySQL, JPA/Hibernate.

---

# Ключевые возможности

* Многопоточная индексация (обход внутренних ссылок с Jsoup).
* Лемматизация (MorphologyService).
* Индекс в MySQL: таблицы `site`, `page`, `lemma`, `search_index`.
* Поиск с TF-IDF ранжированием.
* Генерация сниппетов без HTML, содержащих контекст слов запроса.
* REST API: statistics, start/stop indexing, index single page, search.
* Обновление страницы (indexPage) — не создаёт дубликатов, пересоздаёт индексы.

---

# Требования

* Java 17+
* Maven 3.6+
* MySQL 8.0 
* Рекомендуется: IntelliJ IDEA

---

# Полная инструкция по установке и запуску

## 1. Подготовка MySQL

Войдите в MySQL и выполните:

```sql
CREATE DATABASE search_engine CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- (опционально) создать отдельного пользователя:
CREATE USER 'search_user'@'localhost' IDENTIFIED BY 'StrongPassword!';
GRANT ALL PRIVILEGES ON search_engine.* TO 'search_user'@'localhost';
FLUSH PRIVILEGES;
```

**Важно про кодировку**: в JDBC URL обязательно добавьте `useUnicode=true&characterEncoding=utf8mb4`, чтобы корректно хранить русский текст.

## 2. Настройка `application.yml`

Скопируйте/отредактируйте `src/main/resources/application.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/search_engine?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC
    username: root
    password: 12341k
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
```

## 3. Сборка и запуск

Собрать:

```bash
mvn clean package -DskipTests
```

Запустить:

```bash
java -jar target/search-engine-*.jar
```

Или запустите класс `searchengine.Application` из IDEA.

---

# Как это работает

1. **Crawler** (Jsoup) скачивает HTML, извлекает текст и внутренние ссылки.
2. **MorphologyService** лемматизирует текст (нормальная форма слов).
3. **Indexing** — сохраняются `page`, `lemma`, `search_index` (lemma ↔ page с rank/tf).
4. **Search** — по запросу собираются леммы, берутся TF по страницам и DF, вычисляется TF-IDF, результаты сортируются и возвращаются с сниппетом.

---

# Чек-лист соответствия ТЗ (для защиты, обязательно проверить)

* [x] Все три страницы UI открываются: Dashboard, Management, Search.
* [x] Dashboard показывает статистику (sites, pages, lemmas) — соответствует БД.
* [x] На Management работают Start/Stop индексации.
* [x] Search работает по всем сайтам и по отдельному сайту.
* [x] Результаты поиска содержат title и snippet без HTML.
* [x] Сниппеты содержат слова запроса.
* [x] `POST /api/indexPage` добавляет и обновляет страницу (без дублей).
* [x] Результаты отсортированы по релевантности (TF-IDF реализован).
* [x] README и LICENSE добавлены в репозиторий.
* [ ] (Опционально) Проект доступен по публичному URL для демонстрации (если требуется — задеплоить).

---

# Лицензия

MIT License

---

# Автор

Автор: Daniil Shcharbakou
Email: dan.shcharbakou@gmail.com
