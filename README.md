# Simple Tasker

## REST API for managing tasks and projects.

## Description

Backend application for managing tasks and projects, designed to practice building scalable REST services with Spring Boot.

## Architecture

- Backend: Spring Boot REST API
- Frontend: React SPA (planned)
- Database: PostgreSQL

## Local run with Docker Compose

1. Copy `.env.example` to `.env`
2. Fill the file .env with appropriate data
3. Create `secrets/` if needed
4. Copy `secrets.example/db_password.txt` to `secrets/db_password.txt`
5. Put your real database password into `secrets/db_password.txt`
6. Run `docker compose up -d --build`

## Status

in devellopment (not production ready)

## Tech Stack

- Java 25 (LTS)
- Spring Boot 4
- PostgreSQL
- Docker
- FlyWay
- Hibernate
- Maven

## Roadmap

See [ROADMAP](./ROADMAP.md)
