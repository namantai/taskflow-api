# Explaining TaskFlow in an interview

## What does the project do?

TaskFlow is a REST API for managing tasks. Each task has a title, optional description
and due date, status, priority, and creation/update timestamps. It supports CRUD
(create, read, update, delete) and filtering by status and priority together.
It is a learning project with no authentication or frontend.

## How does a request work?

A client sends HTTP requests with JSON. For example, `POST /api/tasks` creates a task
and returns `201 Created`, the saved task, and its URL in the `Location` header.
GET and PUT return `200`; successful DELETE returns `204` with no body.
PUT replaces all editable fields. Missing optional fields are cleared.

The request passes through **Controller → Service → Repository → Database**.
The response travels back as a DTO, which Spring converts to JSON.

## What does each layer do?

- **Controller:** maps HTTP routes, validates request bodies, and sets HTTP responses.
- **Service:** coordinates task operations, selects filters, handles missing tasks,
  and defines transaction boundaries.
- **Repository:** reads and writes task entities using Spring Data JPA.
- **Model:** defines the database entity and the status/priority enums.
- **DTO:** describes the data clients send and receive. Request DTOs contain no id
  or timestamps, so clients cannot set those server-managed values.

## What is dependency injection?

Instead of a controller creating its own service with `new`, Spring creates the
objects and supplies their dependencies through constructors. This is dependency
injection. It makes dependencies visible and lets a unit test give the service a
mock repository instead of a real database.

## How does Spring Data JPA work?

JPA maps Java objects to database rows. Hibernate is the JPA implementation used here.
`@Entity` marks `Task`; `@Id` and `@GeneratedValue` define its generated primary key.
Enums are stored as names, such as `TODO`, instead of numeric positions.

`TaskRepository` extends `JpaRepository`, which supplies methods such as `save`,
`findById`, and `delete`. Spring also builds queries from names such as
`findByStatusAndPriorityOrderByIdAsc`. We do not write an implementation class.

`@Transactional` groups database work into one transaction. Writes either commit
or roll back. `@PrePersist` sets both timestamps on creation; `@PreUpdate` changes
`updatedAt` when stored fields change. `saveAndFlush` sends the update before mapping
the response, so its timestamp agrees with the database. `createdAt` stays unchanged.

## How does validation work?

`@Valid` tells Spring to validate `TaskRequest`. `@NotBlank` rejects an empty title;
`@Size` requires 3–100 characters. Status and priority are required; description
is optional and limited to 2,000 characters. `dueDate` is optional and uses
`YYYY-MM-DD`. Past due dates are allowed so overdue tasks can be recorded.
Validation or invalid JSON produces `400 Bad Request` before the service changes data.

## Why H2? How do I use PostgreSQL?

H2 runs inside the application and needs no installation. Its default database is
in memory, so restarting TaskFlow loses its tasks. This makes local demonstrations
and automated tests easy to reproduce.

To use PostgreSQL, create the database/user, set `DB_URL`, `DB_USERNAME`, and
`DB_PASSWORD`, and activate the `postgres` profile. The README has Fish commands.
The API code stays the same because JPA handles database access. PostgreSQL keeps
data across application restarts. The demo uses automatic schema updates; a real
deployment should use migrations for predictable schema and data changes.

## How are errors handled?

The service throws `TaskNotFoundException` when an id does not exist.
`GlobalExceptionHandler` converts it to a `404` JSON problem response. Validation
errors return `400` with messages by field. Malformed requests use the same problem
format. Unexpected failures return `500`; internal details are logged on the server.

## How is the project tested?

- Service unit tests use JUnit 5 and Mockito. The repository is mocked, so these
  tests focus on service decisions without starting Spring.
- API integration tests start Spring and H2. MockMvc sends requests through the
  actual controller, service, and repository. Tests check CRUD, filter combinations,
  validation boundaries, persistence, due dates, timestamps, and missing tasks.
- OpenAPI integration tests check that generated documentation describes the API
  and that Swagger UI resources are served.

Run `./mvnw clean verify` to run tests and build the executable JAR. MockMvc does
not open a network port; starting the JAR and using curl checks real HTTP behavior.
Do not describe PostgreSQL as tested unless you have actually run that profile.

## What should I demonstrate?

1. Start the app and open Swagger UI at `http://localhost:8080/swagger-ui.html`.
2. Create a TODO/HIGH task with a due date, then fetch and filter it.
3. Update it to DONE and show that `createdAt` stays fixed while `updatedAt` changes.
4. Submit a two-character title to show `400`, delete the task, then fetch it to show `404`.
5. Open the controller, service, and repository and trace one request. Run the tests.

Know the tradeoffs: the list has no pagination, H2 data is temporary, and there is
no authentication, deployment setup, or schema migration system. Explain these as
the scope of a small learning project, not features that are already implemented.
