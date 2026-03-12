# Patient Management System — Complete Project Documentation

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [Technology Stack](#3-technology-stack)
4. [Services — Detailed Breakdown](#4-services--detailed-breakdown)
   - [Auth Service](#41-auth-service-port-4005)
   - [Patient Service](#42-patient-service-port-4000)
   - [Billing Service](#43-billing-service-port-4001-grpc-9001)
   - [Appointment Service](#44-appointment-service-port-4006)
   - [Analytics Service](#45-analytics-service-port-4002)
   - [API Gateway](#46-api-gateway-port-4004)
5. [Communication Patterns](#5-communication-patterns)
6. [Data Layer](#6-data-layer)
7. [Security](#7-security)
8. [Resilience & Fault Tolerance](#8-resilience--fault-tolerance)
9. [Caching Strategy](#9-caching-strategy)
10. [Kafka Event System](#10-kafka-event-system)
11. [gRPC Service Contracts](#11-grpc-service-contracts)
12. [Observability & Monitoring](#12-observability--monitoring)
13. [AWS Infrastructure (CDK)](#13-aws-infrastructure-cdk)
14. [Containerization & Docker](#14-containerization--docker)
15. [API Reference](#15-api-reference)
16. [Configuration Reference](#16-configuration-reference)
17. [Local Development Setup](#17-local-development-setup)
18. [Integration Tests](#18-integration-tests)
19. [Design Patterns Used](#19-design-patterns-used)
20. [Port Reference](#20-port-reference)

---

## 1. Project Overview

The **Patient Management System** is a production-grade, cloud-native microservices application built to manage patients, appointments, billing accounts, and analytics in a healthcare context.

It uses **Java 21** and **Spring Boot 4.x** across all services, deployed on **AWS ECS Fargate** via **AWS CDK**. The system combines synchronous (gRPC, REST) and asynchronous (Apache Kafka) communication patterns with full observability via Prometheus and AWS CloudWatch.

### Key Numbers
| Metric | Value |
|--------|-------|
| Total Microservices | 6 (+ monitoring) |
| Java Version | 21 (LTS) |
| Spring Boot Version | 4.0.2 / 4.0.3 |
| Kafka Topics | 3 (`patient.created`, `patient.updated`, `billing-account`) |
| gRPC Services | 1 (`BillingService`) |
| Database Instances | 3 (Auth, Patient, Appointment — PostgreSQL) |
| API Gateway Port | 4004 |
| AWS Region | us-east-1 (LocalStack for local simulation) |

---

## 2. Architecture

### High-Level Architecture Diagram

```
                        ┌──────────────────────────────────────────┐
                        │          Application Load Balancer        │
                        │         (AWS ALB — Public Entry Point)    │
                        └──────────────────┬───────────────────────┘
                                           │
                        ┌──────────────────▼───────────────────────┐
                        │             API Gateway  :4004            │
                        │   Spring Cloud Gateway (WebFlux/Reactive) │
                        │   • JWT Validation Filter                 │
                        │   • Redis-backed Rate Limiting (5 req/s)  │
                        │   • Route: /auth/** → Auth Service        │
                        │   • Route: /api/patients/** → Patient Svc │
                        └────┬─────────────────────────────────────┘
                             │
              ┌──────────────┼────────────────────┐
              │              │                    │
   ┌──────────▼────┐  ┌──────▼──────┐  ┌─────────▼──────────┐
   │ Auth Service  │  │Patient Svc  │  │ Appointment Service  │
   │    :4005      │  │   :4000     │  │       :4006          │
   │               │  │             │  │                      │
   │ • JWT Generate│  │ • CRUD API  │  │ • Appointment CRUD   │
   │ • JWT Validate│  │ • Kafka Pub │  │ • Date-Range Query   │
   │ • BCrypt Auth │  │ • Redis Cache│  │ • CachedPatient      │
   │ • PostgreSQL  │  │ • PostgreSQL│  │ • Kafka Consumer     │
   └───────────────┘  └──────┬──────┘  └──────────┬──────────┘
                             │ gRPC (CreateBilling)│ gRPC (CreateBilling)
                             │                    │
                     ┌───────▼────────────────────▼────┐
                     │       Billing Service            │
                     │     REST: :4001  gRPC: :9001     │
                     │                                  │
                     │ • gRPC Server (BillingService)   │
                     │ • Kafka Consumer (billing-account)│
                     │ • Stateless (no DB)              │
                     └──────────────┬───────────────────┘
                                    │
            ┌───────────────────────┴──────────────────────┐
            │              Apache Kafka (AWS MSK)           │
            │   Topics: patient.created, patient.updated,   │
            │           billing-account                     │
            └───────────────────────┬──────────────────────┘
                                    │
                     ┌──────────────▼───────────────┐
                     │       Analytics Service       │
                     │           :4002               │
                     │                               │
                     │ • Kafka Consumer (patient.*)  │
                     │ • Event Aggregation           │
                     │ • No external API             │
                     └───────────────────────────────┘

Shared Infrastructure:
  • Redis (ElastiCache)    — API Gateway rate limiter + Patient Service cache
  • PostgreSQL (RDS x3)   — Auth, Patient, Appointment DBs
  • CloudMap DNS           — Service discovery (patient-management.local)
  • Prometheus + Grafana   — Metrics and dashboards
  • CloudWatch             — Centralized log aggregation
```

### Communication Flow: Create Patient
```
Client
  → POST /api/patients (API Gateway :4004)
    → JWT Validation (calls Auth Service /validate)
    → Route to Patient Service :4000
      → Validate request (JSR-380 Bean Validation)
      → Check email uniqueness (PostgreSQL)
      → gRPC call → Billing Service :9001 (CreateBillingAccount)
          [Circuit breaker: if fails → fallback to Kafka billing-account topic]
      → Save Patient to PostgreSQL
      → Publish Kafka event → patient.created topic
          → Analytics Service consumes (logging/analytics)
          → Appointment Service consumes (caches patient locally)
      → Return PatientResponseDTO
```

---

## 3. Technology Stack

### Core
| Technology | Version | Usage |
|-----------|---------|-------|
| Java | 21 (LTS) | All services |
| Spring Boot | 4.0.2 / 4.0.3 | All services |
| Maven | 3.9.9 | Build tool |

### Service Communication
| Technology | Version | Usage |
|-----------|---------|-------|
| gRPC | 1.69.0 | Patient→Billing, Appointment→Billing |
| Protocol Buffers | 4.29.1 | gRPC serialization & Kafka payloads |
| Apache Kafka | (AWS MSK) | Async event streaming |
| Spring Cloud Gateway | 2025.1.0 | API Gateway routing & filters |
| Spring WebFlux | reactive | API Gateway reactive stack |

### Data
| Technology | Version | Usage |
|-----------|---------|-------|
| Spring Data JPA | Boot-managed | ORM for all services |
| PostgreSQL | 17.2 | Primary relational database |
| H2 | runtime | Dev/test in-memory database |
| Redis | (ElastiCache) | Caching + rate limiting |
| Hibernate | JPA implementation | DDL auto-update |

### Security
| Technology | Version | Usage |
|-----------|---------|-------|
| Spring Security | 6.x | Auth, filter chain |
| JJWT | 0.12.6 | JWT generation & validation |
| BCrypt | Spring Crypto | Password hashing |

### Resilience
| Technology | Version | Usage |
|-----------|---------|-------|
| Resilience4j | 2.3.0 | Circuit breaker, retry |

### Observability
| Technology | Version | Usage |
|-----------|---------|-------|
| Prometheus | latest | Metrics collection |
| Micrometer | Boot-managed | Metrics registry bridge |
| Spring Boot Actuator | Boot-managed | /actuator/prometheus endpoint |
| AWS CloudWatch | — | Centralized log aggregation |
| Grafana | latest | Metrics dashboards |

### Infrastructure & Cloud
| Technology | Version | Usage |
|-----------|---------|-------|
| AWS CDK (Java) | 2.178.1 | Infrastructure as Code |
| AWS Java SDK | 1.12.780 | CDK helper SDK |
| AWS ECS Fargate | — | Container orchestration |
| AWS MSK | Kafka 3.6.0 | Managed Kafka |
| AWS RDS | PostgreSQL 17.2 | Managed DB |
| AWS ElastiCache | Redis | Managed cache |
| AWS CloudMap | — | Service discovery |
| AWS ALB | — | Load balancing |
| LocalStack | — | Local AWS simulation |
| Docker | — | Containerization |

### Testing
| Technology | Version | Usage |
|-----------|---------|-------|
| JUnit 5 | 5.11.4 | Integration tests |
| REST Assured | 5.3.0 | HTTP API testing |

### API Documentation
| Technology | Version | Usage |
|-----------|---------|-------|
| SpringDoc OpenAPI | 2.6.0 / 2.7.0 | Swagger UI & OpenAPI spec |

---

## 4. Services — Detailed Breakdown

### 4.1 Auth Service (Port: 4005)

**Purpose**: Centralized authentication and JWT token management.

**Database**: `auth-service-db` (PostgreSQL)

#### Endpoints
| Method | Path | Description | Auth Required |
|--------|------|-------------|--------------|
| POST | `/login` | Login with email/password, returns JWT | No |
| GET | `/validate` | Validate Bearer JWT token | Bearer Token |

#### Classes
```
AuthServiceApplication.java          — @SpringBootApplication entry point
controller/AuthController.java        — REST controller
service/AuthService.java              — Business logic
service/UserService.java              — User lookup
util/JwtUtil.java                     — JWT generation & validation
model/User.java                       — JPA entity
repository/UserRepository.java        — JPA repository
dto/LoginRequestDTO.java              — Request DTO (email, password)
dto/LoginResponseDTO.java             — Response DTO (token)
config/SecurityConfig.java            — Spring Security config
```

#### Data Model: User
```java
@Entity @Table(name = "users")
class User {
    UUID id               // @Id @GeneratedValue AUTO
    String email          // @Column(unique=true, nullable=false)
    String password       // @Column(nullable=false) — BCrypt encoded
    String role           // @Column(nullable=false)
}
```

#### JWT Token Details
- **Algorithm**: HMAC-SHA (HS256)
- **Claims**: `sub` = email, `role` = user role
- **Expiration**: 10 hours
- **Secret**: Base64-encoded via env var `JWT_SECRET`
- **Library**: JJWT 0.12.6

#### Security Config
- CSRF disabled
- All endpoints permitted (validation is token-based)
- `BCryptPasswordEncoder` bean registered

---

### 4.2 Patient Service (Port: 4000)

**Purpose**: Core patient record management — CRUD, event publishing, billing account creation.

**Database**: `patient-service-db` (PostgreSQL)

#### Endpoints
| Method | Path | Description | Auth Required |
|--------|------|-------------|--------------|
| GET | `/patients` | List patients (paginated, sortable, searchable) | Yes (JWT via Gateway) |
| POST | `/patients` | Create new patient | Yes |
| PUT | `/patients/{id}` | Update patient details | Yes |
| DELETE | `/patients/{id}` | Delete patient | Yes |

#### Query Parameters (GET /patients)
| Param | Default | Description |
|-------|---------|-------------|
| page | 1 | Page number |
| size | 10 | Records per page |
| sort | asc | Sort direction (asc/desc) |
| sortField | name | Field to sort by |
| searchValue | "" | Search by patient name |

#### Classes
```
PatientServiceApplication.java                    — Entry point
controller/PatientController.java                 — REST controller
service/PatientService.java                       — Business logic
model/Patient.java                                — JPA entity
repository/PatientRepository.java                 — JPA repository
dto/PatientRequestDTO.java                        — Input DTO
dto/PatientResponseDTO.java                       — Output DTO
dto/PagedPatientResponseDTO.java                  — Paginated output
dto/validators/CreatePatientValidationGroup.java  — Validation group marker
mapper/PatientMapper.java                         — DTO ↔ Entity converter
kafka/KafkaProducer.java                          — Kafka event publisher
grpc/BillingServiceGrpcClient.java                — gRPC client to Billing
cache/RedisCacheConfig.java                       — Redis cache setup
config/KafkaConfig.java                           — Kafka producer config
exception/GlobalExceptionHandler.java             — @ControllerAdvice
exception/PatientNotFoundException.java           — Custom exception
exception/EmailAlreadyExistsException.java        — Custom exception
aspects/PatientServiceMetrics.java                — AOP metrics (cache miss counter)
```

#### Data Model: Patient
```java
@Entity
class Patient {
    UUID id               // @Id @GeneratedValue AUTO
    String name           // @NotNull
    String email          // @NotNull @Email @Column(unique=true)
    String address        // @NotNull
    LocalDate dateOfBirth // @NotNull
    LocalDate registeredDate // @NotNull
}
```

#### Repository Methods
```java
boolean existsByEmail(String email)
boolean existsByEmailAndIdNot(String email, UUID id)
void deleteById(UUID id)
Page<Patient> findByNameContainingIgnoreCase(String name, Pageable pageable)
```

#### Create Patient Flow
1. Validate `PatientRequestDTO` (Bean Validation)
2. Check `email` uniqueness → throw `EmailAlreadyExistsException` if duplicate
3. Call `BillingServiceGrpcClient.createBillingAccount()` (with circuit breaker)
4. Save `Patient` to PostgreSQL
5. Publish `PatientEvent` to Kafka topic `patient.created`

#### Kafka Topics Published
| Topic | Event Type | Trigger |
|-------|-----------|---------|
| `patient.created` | `PatientEvent` (protobuf) | POST /patients |
| `patient.updated` | `PatientEvent` (protobuf) | PUT /patients/{id} |
| `billing-account` | `BillingAccountEvent` (protobuf) | Circuit breaker fallback |

#### Redis Caching
- `getPatients()` result is cached in Redis
- TTL: **10 minutes**
- Serialization: `GenericJackson2JsonRedisSerializer` with Java time support
- Cache name: configured via `RedisCacheConfig`
- Custom AOP metric: `custom.redis.cache.miss` counter (Micrometer)

#### Resilience4j Circuit Breaker (`billingService`)
```properties
slidingWindowSize=10
minimumNumberOfCalls=5
failureRateThreshold=50%
waitDurationInOpenState=10s
permittedNumberOfCallsInHalfOpenState=3
automaticTransitionFromOpenToHalfOpenEnabled=true
```

#### Resilience4j Retry (`billingRetry`)
```properties
maxAttempts=2
waitDuration=500ms
```

---

### 4.3 Billing Service (Port: 4001, gRPC: 9001)

**Purpose**: Handles billing account creation via gRPC and consumes pending billing events from Kafka.

**Database**: None (stateless service)

#### gRPC Methods
| Method | Request | Response |
|--------|---------|----------|
| `CreateBillingAccount` | `BillingRequest(patientId, name, email)` | `BillingResponse(accountId, status)` |

#### Classes
```
BillingServiceApplication.java                   — Entry point
grpc/BillingGrpcService.java                     — @GrpcService gRPC server
kafka/KafkaConsumer.java                          — Kafka consumer
```

#### gRPC Server Details
```java
@GrpcService
class BillingGrpcService extends BillingServiceImplBase {
    BillingResponse createBillingAccount(BillingRequest request, StreamObserver<BillingResponse> responseObserver)
    // Returns: accountId="1233456", status="ACTIVE"
}
```

#### Kafka Consumer
- **Topic**: `billing-account`
- **Group ID**: `billing-service`
- **Payload**: `BillingAccountEvent` (protobuf)
- **Action**: Processes events when gRPC circuit breaker triggered fallback (creates billing account from queued event)

---

### 4.4 Appointment Service (Port: 4006)

**Purpose**: Manages appointment scheduling and retrieves patient info from a local cached copy.

**Database**: `appointment-service-db` (PostgreSQL)

#### Endpoints
| Method | Path | Description | Auth Required |
|--------|------|-------------|--------------|
| GET | `/appointments` | Get appointments by date range | Yes |

#### Query Parameters
| Param | Type | Description |
|-------|------|-------------|
| from | LocalDateTime | Start of date range |
| to | LocalDateTime | End of date range |

#### Classes
```
AppointmentServiceApplication.java                   — Entry point
controller/AppointmentController.java                — REST controller
service/AppointmentService.java                      — Business logic
entity/Appointment.java                              — JPA entity
entity/CachedPatient.java                            — Local patient cache entity
repository/AppointmentRepository.java                — JPA repository
repository/CachedPatientRepository.java              — JPA repository
dto/AppointmentRequestDTO.java                       — Input DTO
dto/AppointmentResponseDTO.java                      — Output DTO (includes patientName)
kafka/KafkaConsumer.java                             — Patient event consumer
exception/GlobalExceptionHandler.java                — Validation error handler
```

#### Data Models
```java
@Entity
class Appointment {
    UUID id                  // @Id @GeneratedValue AUTO
    UUID patientId           // @NotNull
    LocalDateTime startTime  // @NotNull @Future
    LocalDateTime endTime    // @NotNull @Future
    String reason            // @NotNull
    Long version             // @Version — optimistic locking
}

@Entity @Table(name = "cached_patient")
class CachedPatient {
    UUID id        // Matches patient-service UUID
    String fullName
    String email
    String updatedAt
}
```

#### CachedPatient Pattern
The Appointment Service maintains its own local copy of patient data (CQRS-inspired read model):
- Consumes `patient.created` and `patient.updated` Kafka events
- Stores patient name and email in its own `cached_patient` table
- Joins `Appointment` with `CachedPatient` on `patientId` to return `patientName` in responses
- Eliminates need for synchronous calls to Patient Service

#### Optimistic Locking
`Appointment` entity uses `@Version` for optimistic concurrency control — prevents double-booking race conditions.

---

### 4.5 Analytics Service (Port: 4002)

**Purpose**: Event-driven analytics processing. Consumes patient events for data aggregation and reporting.

**Database**: None
**External API**: None (internal consumer only)

#### Classes
```
AnalyticsServiceApplication.java          — Entry point
kafka/KafkaConsumer.java                  — Patient event consumer
```

#### Kafka Consumer
- **Topic**: `patient` (patient events)
- **Group ID**: `analytics-service`
- **Payload**: `PatientEvent` (protobuf)
- **Action**: Logs PatientId, PatientName, PatientEmail — extensible for analytics storage/aggregation

---

### 4.6 API Gateway (Port: 4004)

**Purpose**: Unified entry point for all external traffic. Handles routing, JWT validation, and rate limiting.

#### Routes
| Route ID | Path Pattern | Target | JWT Required | Notes |
|---------|-------------|--------|-------------|-------|
| `auth-service-route` | `/auth/**` | `http://auth-service:4005` | No | StripPrefix=1 |
| `patient-service-route` | `/api/patients/**` | `http://patient-service:4000` | Yes | JwtValidation filter |
| `api-docs-patient-service` | `/api-docs/patients` | `http://patient-service:4000` | No | RewritePath to /v3/api-docs |

#### Classes
```
ApiGatewayApplication.java                                   — Entry point
filter/JwtValidationGatewayFilterFactory.java                — Custom JWT filter
exception/JwtValidationException.java                        — @RestControllerAdvice
config/WebClientConfig.java                                  — WebClient bean
config/RateLimiterConfig.java                                — IP key resolver
```

#### JWT Validation Filter Flow
```
Request arrives → Extract "Authorization: Bearer {token}" header
  → Missing? → Return 401
  → Present? → WebClient.GET(auth-service/validate)
      → 200 OK? → Pass request through to target service
      → 401 / Error? → Return 401 to client
```

#### Rate Limiting
- **Backend**: Redis (Spring Data Redis Reactive)
- **Algorithm**: Token bucket
- **Burst capacity**: 5 requests
- **Replenish rate**: 5 requests/second
- **Key strategy**: IP address-based

#### Technology
- **Stack**: Spring WebFlux (reactive/non-blocking)
- **Cloud**: Spring Cloud Gateway Server WebFlux (2025.1.0)

---

## 5. Communication Patterns

### Synchronous

#### REST (External)
- Client → API Gateway → Services
- Standard HTTP/1.1 JSON

#### gRPC (Internal Service-to-Service)
| Caller | Target | Method | When |
|--------|--------|--------|------|
| Patient Service | Billing Service :9001 | `CreateBillingAccount` | On patient creation |
| Appointment Service | Billing Service :9001 | `CreateBillingAccount` | On appointment creation |

### Asynchronous

#### Kafka Event Bus
| Topic | Producer | Consumers | Payload |
|-------|---------|----------|---------|
| `patient.created` | Patient Service | Analytics Service, Appointment Service | `PatientEvent` (protobuf) |
| `patient.updated` | Patient Service | Analytics Service, Appointment Service | `PatientEvent` (protobuf) |
| `billing-account` | Patient Service (fallback) | Billing Service | `BillingAccountEvent` (protobuf) |

### Circuit Breaker Fallback Path
```
Patient Service → gRPC → Billing Service
  [Normal path]              ↕ 9001
  
Patient Service → Kafka → billing-account topic → Billing Service Kafka Consumer
  [Fallback path — circuit breaker open]
```

---

## 6. Data Layer

### Databases (Per-Service, No Sharing)
| Service | DB Name | Type | Tables |
|---------|---------|------|--------|
| Auth Service | auth-service-db | PostgreSQL 17.2 | `users` |
| Patient Service | patient-service-db | PostgreSQL 17.2 | `patient` |
| Appointment Service | appointment-service-db | PostgreSQL 17.2 | `appointment`, `cached_patient` |
| Billing Service | — | None | — |
| Analytics Service | — | None | — |
| API Gateway | — | None | — |

### DDL Strategy
- `spring.jpa.hibernate.ddl-auto=update` (auto schema migration on startup)
- `spring.sql.init.mode=always` (run SQL init scripts on startup)
- `spring.jpa.defer-datasource-initialization=true` (init after Hibernate schema creation)

### Redis Usage
| Usage | Service | Config |
|-------|---------|-------|
| Patient list cache | Patient Service | TTL 10 min, JSON serialization |
| Rate limiting counter | API Gateway | Token bucket per IP |

---

## 7. Security

### Authentication Flow
```
1. Client: POST /auth/login  { email, password }
2. Auth Service: Find user by email (UserRepository)
3. Auth Service: BCrypt.matches(password, stored_hash)
4. Auth Service: JwtUtil.generateToken(email, role)
5. Auth Service: Return { token: "eyJ..." }
6. Client: Include header  Authorization: Bearer eyJ...
7. API Gateway: JwtValidationFilter intercepts
8. API Gateway: WebClient calls Auth Service /validate
9. Auth Service: JwtUtil.validateToken(token) — checks signature + expiry
10. API Gateway: Pass through to target service
```

### Security Layers
| Layer | Mechanism | Implementation |
|-------|-----------|-----------------|
| Password Storage | BCrypt hashing | `BCryptPasswordEncoder` |
| Token Generation | JWT (HMAC-SHA) | JJWT 0.12.6 |
| Token Transmission | Bearer token in header | HTTP Authorization header |
| Gateway Filter | Token proxy validation | `JwtValidationGatewayFilterFactory` |
| Network | Private subnets | All services except API Gateway |
| Secrets | Env vars + AWS Secrets Manager | `JWT_SECRET`, DB credentials |

### JWT Payload Structure
```json
{
  "sub": "user@example.com",
  "role": "USER",
  "iat": 1234567890,
  "exp": 1234603890
}
```

### Environment Variable: JWT Secret
```
JWT_SECRET=Y2hhVEc3aHJnb0hYTzMyZ2ZqVkpiZ1RkZG93YWxrUkM=
```
(Base64-encoded, injected as environment variable — not hardcoded in application code)

---

## 8. Resilience & Fault Tolerance

### Circuit Breaker (Resilience4j) — Patient Service
Applied on `BillingServiceGrpcClient.createBillingAccount()`:

| Config | Value |
|--------|-------|
| Name | `billingService` |
| Sliding window size | 10 calls |
| Minimum calls before CB logic | 5 |
| Failure rate to open | 50% |
| Wait duration in OPEN state | 10 seconds |
| Calls in HALF-OPEN | 3 |
| Auto transition HALF-OPEN | enabled |

### Retry (Resilience4j) — Patient Service
| Config | Value |
|--------|-------|
| Name | `billingRetry` |
| Max attempts | 2 |
| Wait between retries | 500ms |

### Circuit Breaker States
```
CLOSED (normal) → failure rate > 50% → OPEN (fail fast for 10s)
    ↑                                         ↓
    ← ← ← ← ← success (>50%) ← ← HALF-OPEN (test 3 calls)
```

### Fallback Behavior
When circuit is OPEN, `billingFallback()` is invoked:
- Publishes `BillingAccountEvent` to Kafka topic `billing-account`
- Returns a pending status response
- Billing Service Kafka consumer processes this later (eventual consistency)

---

## 9. Caching Strategy

### Patient List Cache (Redis)
```java
// RedisCacheConfig.java
RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
    .entryTtl(Duration.ofMinutes(10))
    .serializeValuesWith(
        RedisSerializationContext.SerializationPair.fromSerializer(
            new GenericJackson2JsonRedisSerializer(objectMapper)
        )
    );
```

### Cache Invalidation
- Cache is **write-through** on updates
- TTL-based expiry (10 minutes)
- Manual eviction on create/update operations

### Cache Metrics
AOP aspect `PatientServiceMetrics.java` wraps `getPatients()`:
- Records cache miss using Micrometer counter: `custom.redis.cache.miss`
- Exposed via `/actuator/prometheus`

---

## 10. Kafka Event System

### Topics
| Topic | Key Type | Value Type | Partitions |
|-------|---------|-----------|-----------|
| `patient.created` | String (patientId) | byte[] (PatientEvent protobuf) | Default |
| `patient.updated` | String (patientId) | byte[] (PatientEvent protobuf) | Default |
| `billing-account` | String | byte[] (BillingAccountEvent protobuf) | Default |

### Producer Config (Patient Service)
```properties
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.ByteArraySerializer
spring.kafka.bootstrap-servers=localhost:9092  # overridden by env var in Docker
```

### Consumer Configs
| Service | Group ID | Topics Subscribed |
|---------|---------|------------------|
| Analytics Service | `analytics-service` | `patient` |
| Appointment Service | `appointment-service` | `patient.created`, `patient.updated` |
| Billing Service | `billing-service` | `billing-account` |

```properties
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer
```

### Message Flow Example
```
Patient Service publishes:
  topic=patient.created
  key="550e8400-e29b-41d4-a716-446655440000"
  value=PatientEvent {
    patientId: "550e8400-e29b-41d4-a716-446655440000"
    name: "John Doe"
    email: "john@example.com"
    event_type: "PATIENT_CREATED"
  }

Consumers:
  → Analytics Service: logs event details
  → Appointment Service: upserts CachedPatient record
```

---

## 11. gRPC Service Contracts

### billing_service.proto
```protobuf
syntax = "proto3";
option java_multiple_files = true;
option java_package = "billing";

service BillingService {
    rpc CreateBillingAccount (BillingRequest) returns (BillingResponse);
}

message BillingRequest {
    string patientId = 1;
    string name = 2;
    string email = 3;
}

message BillingResponse {
    string accountId = 1;
    string status = 2;
}
```

### patient_event.proto
```protobuf
syntax = "proto3";
package patient.events;
option java_multiple_files = true;

message PatientEvent {
    string patientId = 1;
    string name = 2;
    string email = 3;
    string event_type = 4;
}
```

### billing_account_event.proto
```protobuf
syntax = "proto3";
package billing.events;
option java_multiple_files = true;

message BillingAccountEvent {
    string patientId = 1;
    string name = 2;
    string email = 3;
    string event_type = 4;
}
```

### gRPC Connection Config
- **Client** (Patient Service): Connects to `billing-service:9001`
- **Server** (Billing Service): Listens on `grpc.server.port=9001`
- **Stub type**: Blocking stub (`BillingServiceBlockingStub`)
- **Protocol**: HTTP/2

---

## 12. Observability & Monitoring

### Metrics Pipeline
```
Service → Micrometer → /actuator/prometheus → Prometheus → Grafana
```

### Spring Boot Actuator Endpoints (Patient Service)
```properties
management.endpoints.web.exposure.include=health,info,prometheus,metrics,cache
management.prometheus.metrics.export.enabled=true
```

### Prometheus Configuration
```yaml
# prometheus.yml (local dev)
global:
  scrape_interval: 5s

scrape_configs:
  - job_name: 'patient-service'
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ['patient-service:4000']
```

```yaml
# prometheus-prod.yml (AWS ECS)
static_configs:
  - targets: ['patient-service.patient-management.local:4000']
```

### Custom Metrics
| Metric Name | Type | Description |
|-------------|------|-------------|
| `custom.redis.cache.miss` | Counter | Incremented on each cache miss in `getPatients()` |

### AWS CloudWatch
- Log retention: **1 day**
- Each Fargate service has its own CloudWatch log group
- Configured in CDK during container definition

### Grafana
- Port: **3000**
- Deployed as Fargate service alongside other services
- Reads from Prometheus

---

## 13. AWS Infrastructure (CDK)

All AWS resources are defined in Java using AWS CDK 2.178.1 in `infrastructure/`.

### Stack: `LocalStack` (extends `software.amazon.awscdk.Stack`)

#### VPC
```
PatientManagementVPC
  - Max AZs: 2
  - Includes public and private subnets
```

#### RDS PostgreSQL Instances
| Instance | DB Name | Engine | Instance Class | Storage |
|----------|---------|--------|---------------|---------|
| auth-service-db | — | PostgreSQL 17.2 | Burstable2.micro | 20 GB |
| patient-service-db | — | PostgreSQL 17.2 | Burstable2.micro | 20 GB |

#### MSK Kafka Cluster
```
Name: kafka-cluster
Version: 3.6.0
Brokers: 2
Instance: kafka.m5.xlarge
```

#### ElastiCache Redis
```
Engine: Redis
Node type: cache.t2.micro
Cluster mode: single node
```

#### ECS Fargate Services
| Service | CPU | Memory | Port(s) |
|---------|-----|--------|---------|
| auth-service | 256 | 512 MB | 4005 |
| billing-service | 256 | 512 MB | 4001, 9001 |
| analytics-service | 256 | 512 MB | 4002 |
| patient-service | 256 | 512 MB | 4000 |
| appointment-service | 256 | 512 MB | 4006 |
| prometheus | 256 | 512 MB | 9090 |
| grafana | 256 | 512 MB | 3000 |
| api-gateway | 256 | 512 MB | 4004 (via ALB) |

#### Application Load Balancer
- Public-facing
- Routes all external traffic to API Gateway service (port 4004)
- DNS name retrieved via CloudFormation output

#### CloudMap (Service Discovery)
```
Namespace: patient-management.local
DNS format: {service-name}.patient-management.local
Example: billing-service.patient-management.local:9001
```

#### CloudWatch Log Groups
- Retention: 1 day per service
- Auto-created per Fargate service

### Deployment Script (`localstack-deploy.sh`)
```bash
# 1. Generate CDK CloudFormation template (cdk synth)
# 2. Create S3 bucket in LocalStack
# 3. Upload CFN template to S3
# 4. Delete existing stack (if present)
# 5. Create new CloudFormation stack
# 6. Wait for CREATE_COMPLETE
# 7. Query and print ALB DNS name
```

---

## 14. Containerization & Docker

### Dockerfile Pattern (All Services)
Multi-stage build for minimal image size and dependency caching:

```dockerfile
# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B           # Cache dependencies layer
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21-jdk AS runner
WORKDIR /app
COPY --from=builder /app/target/{service}-0.0.1-SNAPSHOT.jar ./app.jar
EXPOSE {port}
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Monitoring Dockerfile
```dockerfile
FROM prom/prometheus:latest
COPY prometheus-prod.yml /etc/prometheus/prometheus.yml
```

### Docker Compose (Patient Service — Local Dev)
```yaml
version: "3.9"
services:
  patient-service:
    image: patient-service:latest
    ports: ["4000:4000"]
    environment:
      BILLING_SERVER_GRPC_PORT: 9001
      BILLING_SERVICE_ADDRESS: billing-service
      SPRING_CACHE_TYPE: redis
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_DATASOURCE_URL: jdbc:postgresql://patient-service-db:5432/db
      SPRING_DATASOURCE_USERNAME: admin_user
      SPRING_DATASOURCE_PASSWORD: password
      SPRING_JPA_HIBERNATE_DDL_AUTO: update
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      SPRING_SQL_INIT_MODE: always
    networks: [internal]

networks:
  internal:
    external: true
```

---

## 15. API Reference

### Auth Service Endpoints

**POST /login**
```json
Request:
{
  "email": "doctor@hospital.com",
  "password": "securepassword123"
}

Response 200:
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}

Response 401: Invalid credentials
```

**GET /validate**
```
Request Header: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Response 200: (valid token)
Response 401: (invalid/expired token)
```

---

### Patient Service Endpoints

**GET /patients**
```
Query: page=1&size=10&sort=asc&sortField=name&searchValue=john

Response 200:
{
  "patients": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "John Doe",
      "email": "john.doe@example.com",
      "address": "123 Main St",
      "dateOfBirth": "1985-06-15"
    }
  ],
  "page": 1,
  "totalPages": 5,
  "size": 10,
  "totalElements": 48
}
```

**POST /patients**
```json
Request:
{
  "name": "Jane Smith",
  "email": "jane.smith@example.com",
  "address": "456 Oak Ave",
  "dateOfBirth": "1990-03-22",
  "registeredDate": "2024-01-15"
}

Response 200:
{
  "id": "660e9400-f29c-52e5-b827-557766550001",
  "name": "Jane Smith",
  "email": "jane.smith@example.com",
  "address": "456 Oak Ave",
  "dateOfBirth": "1990-03-22"
}

Response 400: Validation error / email already exists
```

**PUT /patients/{id}**
```json
Request: Same as POST (registeredDate not required)
Response 200: Updated PatientResponseDTO
Response 404: Patient not found
Response 400: Email conflict
```

**DELETE /patients/{id}**
```
Response 204: No Content
Response 404: Patient not found
```

---

### Appointment Service Endpoints

**GET /appointments**
```
Query: from=2026-01-01T08:00:00&to=2026-01-31T18:00:00

Response 200:
[
  {
    "id": "770fa500-g30d-63f6-c938-668877660002",
    "patientId": "550e8400-e29b-41d4-a716-446655440000",
    "patientName": "John Doe",
    "startTime": "2026-01-15T09:00:00",
    "endTime": "2026-01-15T10:00:00",
    "reason": "Annual checkup",
    "version": 0
  }
]
```

---

### gRPC Endpoint (Billing Service)

**BillingService/CreateBillingAccount**
```protobuf
Request:
BillingRequest {
  patientId: "550e8400-e29b-41d4-a716-446655440000"
  name: "John Doe"
  email: "john.doe@example.com"
}

Response:
BillingResponse {
  accountId: "1233456"
  status: "ACTIVE"
}
```

---

## 16. Configuration Reference

### Environment Variables (All Services)

#### Auth Service
| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | — | Base64 HMAC secret key |
| `SPRING_DATASOURCE_URL` | — | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | — | DB username |
| `SPRING_DATASOURCE_PASSWORD` | — | DB password |

#### Patient Service
| Variable | Default | Description |
|----------|---------|-------------|
| `BILLING_SERVICE_ADDRESS` | `billing-service` | gRPC host |
| `BILLING_SERVER_GRPC_PORT` | `9001` | gRPC port |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `SPRING_DATA_REDIS_HOST` | `redis` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `SPRING_CACHE_TYPE` | `redis` | Cache backend |
| `SPRING_DATASOURCE_URL` | — | PostgreSQL JDBC URL |

#### API Gateway
| Variable | Default | Description |
|----------|---------|-------------|
| `REDIS_HOST` | `redis` | Redis host for rate limiter |
| `REDIS_PORT` | `6379` | Redis port |
| `AUTH_SERVICE_URL` | `http://auth-service:4005` | Auth service base URL |

#### Appointment Service
| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://appointment-service-db:5432/db` | DB URL |
| `SPRING_DATASOURCE_USERNAME` | `admin_user` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `password` | DB password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Kafka brokers |
| `SPRING_KAFKA_CONSUMER_GROUP_ID` | `appointment-service` | Consumer group |

---

## 17. Local Development Setup

### Prerequisites
- Java 21 (JDK)
- Maven 3.9.x
- Docker + Docker Compose
- LocalStack CLI (for AWS simulation)

### Shared Docker Network
```bash
docker network create internal
```

### Start Infrastructure (in order)
```bash
# 1. PostgreSQL databases
docker run -d --name patient-service-db --network internal \
  -e POSTGRES_DB=db -e POSTGRES_USER=admin_user -e POSTGRES_PASSWORD=password \
  -p 5432:5432 postgres:17.2

# 2. Redis
docker run -d --name redis --network internal -p 6379:6379 redis:latest

# 3. Kafka (use KRaft or ZooKeeper mode)
# (Use bitnami/kafka image or confluent platform)

# 4. Start each service via docker-compose or mvn spring-boot:run
```

### Run a Service Locally
```bash
cd patient-service
mvn clean spring-boot:run
```

### Build Docker Image
```bash
cd patient-service
docker build -t patient-service:latest .
```

### Deploy to LocalStack (AWS Simulation)
```bash
cd infrastructure
bash localstack-deploy.sh
```

---

## 18. Integration Tests

Located in `integeration-test/` module.

### Dependencies
- **JUnit 5** (5.11.4) — Test framework
- **REST Assured** (5.3.0) — HTTP API assertions

### Purpose
End-to-end testing across the API Gateway, verifying:
- Auth flow (login → get token)
- Patient CRUD operations
- JWT-protected route enforcement

---

## 19. Design Patterns Used

### Architectural Patterns
| Pattern | Where Applied |
|---------|--------------|
| **Microservices** | Entire system — 6 autonomous services |
| **API Gateway** | Single entry point (Spring Cloud Gateway) |
| **Database per Service** | Each service owns its own DB |
| **Event-Driven Architecture** | Kafka for async communication |
| **CQRS (partial)** | Appointment Service — CachedPatient read model |
| **Saga (choreography)** | Patient create → triggers distributed events |
| **Service Discovery** | AWS CloudMap DNS-based |

### Resilience Patterns
| Pattern | Implementation |
|---------|---------------|
| **Circuit Breaker** | Resilience4j on Billing gRPC calls |
| **Retry** | Resilience4j retry on transient failures |
| **Fallback** | Kafka fallback when circuit is OPEN |
| **Bulkhead** | Service isolation (separate processes) |
| **Rate Limiting** | Redis token bucket at API Gateway |

### Code Patterns
| Pattern | Where Applied |
|---------|--------------|
| **DTO Pattern** | All services — Request/Response DTOs |
| **Mapper Pattern** | PatientMapper (DTO ↔ Entity) |
| **Repository Pattern** | Spring Data JPA repositories |
| **AOP (Aspect-Oriented)** | `PatientServiceMetrics` — cache miss tracking |
| **Validation Groups** | `CreatePatientValidationGroup` — conditional validation |
| **Optimistic Locking** | `@Version` on Appointment entity |

---

## 20. Port Reference

| Service | REST Port | gRPC Port |
|---------|----------|----------|
| Patient Service | 4000 | — |
| Billing Service | 4001 | 9001 |
| Analytics Service | 4002 | — |
| API Gateway | 4004 | — |
| Auth Service | 4005 | — |
| Appointment Service | 4006 | — |
| Prometheus | 9090 | — |
| Grafana | 3000 | — |
| Redis | 6379 | — |
| PostgreSQL | 5432 | — |
| Kafka | 9092 | — |

---

*Generated: March 12, 2026*
