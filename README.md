# LoadFlow Server

[![Actions Status](https://github.com/gridsuite/loadflow-server/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/gridsuite/loadflow-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Aloadflow-server&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Aloadflow-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)

## Description

The **loadflow-server** is a microservice of the [GridSuite](https://github.com/gridsuite) platform dedicated to **power network load flow computation**.

It provides the following capabilities:

- **Run load flow computations** on a network using configurable providers (OpenLoadFlow, DynaFlow).
- **Apply solved values** back to the network (tap changer positions, shunt compensator section counts) after a converged computation.
- **Detect and store limit violations** (current, voltage) with enriched metadata (overload durations, PATL limits, upcoming overloads).
- **Compute energy balance** per connected component and per country (load, generation, losses, net positions, cross-border exchanges).
- **Store** results in a relational database and **query** them with filtering, sorting, and pagination.
- **Manage parameter sets** (create, read, update, duplicate, delete) with provider-aware limit reduction configurations.
- Run computations either **synchronously** (direct response) or **asynchronously** (via a RabbitMQ message queue).

---

## Technical Stack

| Element | Detail |
|---|---|
| Language | Java 17+ |
| Framework | Spring Boot (Web, Data JPA, Actuator, Cloud Stream) |
| Build tool | Maven (`powsybl-parent-ws`) |
| Database | PostgreSQL (production), H2 (tests) |
| Schema migrations | Liquibase |
| Messaging | RabbitMQ via Spring Cloud Stream |
| API documentation | OpenAPI / Swagger (`springdoc`) |
| Metrics | Micrometer / Prometheus |
| Containerization | Google Jib, base image `powsybl/java-dynawo` |

### Computation Providers (via PowSyBl SPI)

| Provider | Description |
|---|---|
| `OpenLoadFlow` | Default provider |
| `DynaFlow` | Alternative provider (Dynawo image) |

You can find [information on openLoadFlow here](https://github.com/powsybl/powsybl-open-loadflow)

---

## Interactions with Other Microservices

```
┌──────────────────────┐
│   loadflow-server    │──► network-store-server  (read/write network topology)
│                      │──► filter-server          (resolve equipment filters for limit violations)
│                      │──► report-server          (post computation functional logs)
└──────────────────────┘
         ▲  ▼
      RabbitMQ (loadflow.run / loadflow.cancel / loadflow.result / loadflow.stopped)
```

---

## Asynchronous Execution Flow

1. The controller publishes a message on the `loadflow.run` queue.
2. Four parallel consumers (`consumeRun1` through `consumeRun4`) process messages concurrently for load balancing (effective parallelism = 4).
3. The computation result is published on `loadflow.result`.
4. Cancellation of a running computation goes through the `loadflow.cancel` queue.
5. Dead-letter queues (`loadflow.run.dlx`) and quorum queues ensure reliability.

---

## Result Data

A load flow result is composed of several complementary datasets exposed through the REST API:

| Dataset | Description |
|---|---|
| **Component results** | Per-electrical-island results: status, iteration count, distributed active power, energy balance (consumption, generation, losses, exchanges). Supports filtering, sorting, and pagination. |
| **Limit violations** | Detected current and voltage limit violations with enriched metadata: overload duration, PATL limit, upcoming overload, next limit name. Supports global filters (network-element-based), column filters, sorting, and pagination. |
| **Modifications** | Tap changer positions and shunt compensator section counts applied to the network when `applySolvedValues=true` (stored as JSON). |
| **Country adequacy** | Per-country energy balance: load, generation, losses, net position, and cross-border exchange matrix. |

---

## Architectural Choices & Patterns

- **Template Method pattern via shared `computation` library**: core service, worker, result, and observer classes extend abstract base classes (`AbstractComputationService`, `AbstractWorkerService`, `AbstractComputationResultService`, `AbstractComputationObserver`) from the shared `gridsuite-computation` library, enforcing a consistent async pipeline across all computation servers in the GridSuite ecosystem.
- **Simulated RabbitMQ consumer concurrency**: four identical `@Bean Consumer<Message<String>>` methods (`consumeRun1–4`) are declared, each creating an independent listener container, achieving an effective parallelism of 4 with individual thread-level control.
- **JPA Specification pattern**: all filtered/paginated queries use dedicated `*SpecificationBuilder` classes (`LimitViolationsSpecificationBuilder`, `ComponentResultSpecificationBuilder`, `SlackBusResultSpecificationBuilder`), keeping query logic decoupled from repositories and controllers.
- **Provider-aware limit reduction duality**: for `OpenLoadFlow`, a full voltage-level × duration-band reduction matrix (`LimitReductionsByVoltageLevel`) is used; for all other providers, a single scalar `limitReduction` float is passed directly to `Security.checkLimits()`.
- **CLOB-based JSON storage for modifications**: `LoadFlowModificationInfos` (tap changer and shunt positions) is serialised to JSON and stored as a CLOB column, avoiding a complex relational schema for a flexible map structure.
- **Micrometer observability**: all major computation steps (network loading, computation execution, result saving, network flushing) are wrapped in named Micrometer observations via `LoadFlowObserver`, enabling distributed tracing and metric collection without cluttering business logic.

---
