# LoadFlow Server

[![Actions Status](https://github.com/gridsuite/loadflow-server/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/gridsuite/loadflow-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Aloadflow-server&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Aloadflow-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)

## Description

The **loadflow-server** is a microservice of the [GridSuite](https://github.com/gridsuite) platform dedicated to **power network load flow computation**.

It provides the following capabilities:

- **Run load flow computations** on a network using configurable providers (OpenLoadFlow, DynaFlow).
- **Detect and store limit violations** (current, voltage, active power, ...) with enriched metadata (overload durations, PATL limits, upcoming overloads).
- **Compute energy balance** per connected component and per country (load, generation, losses, net positions, cross-border exchanges).
- **Store** results in a relational database and **query** them with filtering, sorting, and pagination.
- **Manage parameter sets** (create, read, update, duplicate, delete) with provider-aware limit reduction configurations.
- Run computations either **synchronously** (direct response) or **asynchronously** (via a RabbitMQ message queue).


---
## Technical Stack

- Spring Boot (Web, Data JPA, Actuator, Cloud Stream)
- PostgreSQL
- Liquibase
- RabbitMQ via Spring Cloud Stream
- API documentation : OpenAPI / Swagger (`springdoc`)
- Micrometer / Prometheus
- [gridsuite-computation](https://github.com/gridsuite/computation)

---



## Development Scripts


Build Docker image

```shell
mvn install -DskipTests -Dpowsybl.docker.install
```

Please read [liquibase usage](https://github.com/powsybl/powsybl-parent/#liquibase-usage) for instructions to automatically generate changesets. After you generated a changeset do not forget to add it to git and in src/resource/db/changelog/db.changelog-master.yml

---

## Interactions with Other Microservices

```text
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
2. Parallel consumers (`consumeRun1` through `consumeRunX`) process messages concurrently for load balancing.
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

## Micrometer observability

All major computation steps (network loading, computation execution, result saving, network flushing) are wrapped in named Micrometer observations via `LoadFlowObserver`, enabling distributed tracing and metric collection without cluttering business logic.

---

## Built on gridsuite-computation

The following capabilities are provided by the gridsuite-computation shared library:

 - asynchronous run/cancel pipeline,
 - transactional result notifications,
 - network equipment filtering,
 - report integration,
 - Micrometer observability.

The loadflow-server itself focuses on load flow-specific logic (parameters, result model, providers) and delegates the common computation infrastructure to this lib.

---


## Useful Links

You can find [information on openLoadFlow here](https://powsybl.readthedocs.io/projects/powsybl-open-loadflow/en/latest/)


