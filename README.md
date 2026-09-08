# Simple Tasker

## REST API for managing tasks and projects.

## Description

Backend application for managing tasks and projects, designed to practice building scalable REST services with Spring Boot.

## Architecture

- Backend: Spring Boot REST API
- Frontend: React + TypeScript SPA (Vite)
- Database: PostgreSQL

## Local run with Docker Compose

1. Copy `.env.example` to `.env`
2. Fill the file .env with appropriate data
3. Create `secrets/` if needed
4. Copy `secrets.example/db_password.txt` to `secrets/db_password.txt`
5. Put your real database password into `secrets/db_password.txt`
6. Run `docker compose up -d --build`

## Status

in development (not production ready)

## Frontend

After starting the backend using the steps above, run the frontend in a separate terminal:

```sh
cd frontend
npm ci
npm run dev
```

Open http://127.0.0.1:5173. Node.js 22.12+ or 24+ is required.
The local frontend server forwards `/api` to `http://localhost:8080`.
If the backend uses another port, set `API_PROXY_TARGET` in `frontend/.env.local`.

The UI supports task creation, editing, deletion with confirmation, details, status transitions, server-side
filtering, sorting and pagination. See [frontend/README.md](./frontend/README.md)
for the HTTP contract, tests, build and deployment boundary.

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
