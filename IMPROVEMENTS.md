# Light Kafka Viewer - Improvement Roadmap

> Analysis date: 2026-03-10
> Based on comparison with: Conduktor, Offset Explorer, Kafka UI (Provectus), AKHQ

---

## Table of Contents

1. [High Priority Features](#1-high-priority-features)
2. [Medium Priority Features](#2-medium-priority-features)
3. [Lower Priority Features](#3-lower-priority-features)
4. [Technical Improvements](#4-technical-improvements)
5. [Implementation Order](#5-recommended-implementation-order)

---

## 1. High Priority Features

### 1.1 Consumer Groups Management ✅ IMPLEMENTED

**Priority:** 🔴 Critical
**Effort:** Medium (3-5 days)
**Reference:** All major Kafka tools

#### Feature Description
- View all consumer groups in a cluster
- Monitor consumer group lag per partition
- View consumer group members and their assignments
- Reset offsets (to earliest, latest, or specific timestamp)
- Delete consumer groups

#### Implementation Status

**Completed:**
- Cluster group list with search, state, member count, and topic count
- Group details with assignments and committed, end, and lag offsets per partition
- Offset reset to earliest, latest, absolute offset, or timestamp for all partitions of a topic
- Consumer group deletion with explicit confirmation and broker error reporting
- Coroutine service wrapper with validation, timeout handling, and lifecycle cleanup

---

### 1.2 Topic Configuration Management ✅ IMPLEMENTED

**Priority:** 🔴 Critical
**Effort:** Medium (2-4 days)
**Reference:** Conduktor, Kafka UI

#### Feature Description
- View all topic configuration parameters
- Edit configurable parameters (retention, compaction, etc.)
- View partition details (ISR, leader, replicas)
- Add partitions to existing topics
- View topic statistics (message count, size)

#### Implementation Status
**Completed:**
- `TopicConfigDialog.kt` - Dialog with Config/Partitions/Statistics tabs
- `KafkaModels.kt` - Added `TopicConfig`, `TopicConfigEntry`, `PartitionDetail`, `TopicStats`
- `KafkaClientPorts.kt` - Added `getTopicConfig()`, `updateTopicConfig()`, `addPartitions()`, `getPartitionDetails()`
- `KafkaClientAdapters.kt` - Implemented all methods using Kafka AdminClient API
- `KafkaAdminService.kt` - Added service methods with `KafkaResult<T>` wrappers

---

### 1.3 Schema Registry Integration

**Priority:** 🔴 High
**Effort:** Large (5-7 days)
**Reference:** Conduktor, AKHQ, Kafka UI

#### Feature Description
- Connect to Confluent Schema Registry
- Browse subjects and schemas
- View schema versions and compatibility
- Decode Avro/Protobuf messages using registry
- Register new schemas
- Check compatibility

#### Implementation Plan

**New Module:**
```
schema-registry/
├── build.gradle.kts
└── src/main/kotlin/com/lightkafka/schemaregistry/
    ├── SchemaRegistryClient.kt       # HTTP client for schema registry
    ├── SchemaRegistryPort.kt         # Port interface
    ├── model/
    │   ├── SchemaInfo.kt
    │   └── SubjectInfo.kt
    └── deserializers/
        ├── AvroDeserializer.kt
        └── ProtobufDeserializer.kt
```

**Core Models:**
```kotlin
data class SchemaRegistryConfig(
    val url: String,
    val auth: AuthConfig?,             // Basic auth or API key
    val cacheSchemas: Boolean = true
)

data class SubjectInfo(
    val subject: String,
    val compatibility: CompatibilityLevel,
    val versions: List<Int>,
    val latestVersion: Int
)

data class SchemaInfo(
    val subject: String,
    val version: Int,
    val id: Int,
    val schemaType: SchemaType,        // AVRO, PROTOBUF, JSON
    val schema: String,
    val references: List<SchemaReference>?
)

enum class CompatibilityLevel {
    NONE, BACKWARD, BACKWARD_TRANSITIVE,
    FORWARD, FORWARD_TRANSITIVE,
    FULL, FULL_TRANSITIVE
}
```

**Port Interface:**
```kotlin
interface SchemaRegistryPort {
    suspend fun getSubjects(): Result<List<String>>
    suspend fun getSubjectVersions(subject: String): Result<List<Int>>
    suspend fun getSchema(subject: String, version: Int): Result<SchemaInfo>
    suspend fun getSchemaById(id: Int): Result<SchemaInfo>
    suspend fun registerSchema(subject: String, schema: SchemaInfo): Result<Int>
    suspend fun checkCompatibility(subject: String, version: Int, schema: String): Result<Boolean>
    suspend fun getConfig(): Result<CompatibilityLevel>
    suspend fun setCompatibility(level: CompatibilityLevel): Result<Unit>
}
```

**UI Components:**
```
ui/src/main/kotlin/com/lightkafka/ui/
├── SchemaRegistryPane.kt            # Browse subjects/schemas
├── SchemaDetailPane.kt              # View schema with diff between versions
├── SchemaRegisterDialog.kt          # Register new schema
└── SchemaRegistryConfigDialog.kt    # Configure connection
```

**Integration Points:**
1. Add Schema Registry URL to connection profile
2. Auto-detect schema from message headers (`schemaId` field)
3. Add "Decode with Schema Registry" option in InspectorPane
4. Show schema info when viewing Avro/Protobuf messages

---

### 1.4 Topic Delete and Enhanced Management ✅ IMPLEMENTED

**Priority:** 🔴 High
**Effort:** Small (1-2 days)
**Reference:** All major tools

#### Feature Description
- Delete topics (with confirmation)
- Create topics with configurable partitions/replication
- Clone topic configuration

#### Implementation Status
**Completed:**
- `KafkaModels.kt` - Added `CreateTopicRequest` model
- `KafkaClientPorts.kt` - Added `deleteTopic()`, enhanced `createTopic()`
- `KafkaClientAdapters.kt` - Implemented using Kafka AdminClient API
- `KafkaAdminService.kt` - Added service methods with `KafkaResult<T>` wrappers
- `MainUiState.kt` - Added `topicToDelete`, topic management actions
- `MainLayoutPanes.kt` - Added context menu with Delete option, delete confirmation dialog
- `App.kt` - Added `deleteTopic` callback

---

## 2. Medium Priority Features

### 2.1 Kafka Connect UI

**Priority:** 🟡 Medium
**Effort:** Large (5-7 days)
**Reference:** Conduktor, Kafka UI, AKHQ

#### Feature Description
- List connectors and their status
- View connector configuration
- Create/edit/delete connectors
- Pause/resume/restart connectors
- View connector tasks and errors

#### Implementation Plan

**New Module:**
```
kafka-connect/
├── build.gradle.kts
└── src/main/kotlin/com/lightkafka/kafkaconnect/
    ├── KafkaConnectClient.kt
    ├── KafkaConnectPort.kt
    └── model/
        ├── ConnectorInfo.kt
        └── TaskInfo.kt
```

**Core Models:**
```kotlin
data class ConnectorInfo(
    val name: String,
    val type: ConnectorType,           // SOURCE, SINK
    val state: ConnectorState,         // RUNNING, PAUSED, FAILED, etc.
    val workerId: String,
    val config: Map<String, String>,
    val tasks: List<TaskInfo>
)

data class TaskInfo(
    val id: Int,
    val state: TaskState,
    val workerId: String,
    val trace: String?                 // Error trace if failed
)
```

**UI Components:**
```
ui/src/main/kotlin/com/lightkafka/ui/
├── ConnectPane.kt                    # Connectors list
├── ConnectorDetailPane.kt            # Config and tasks
└── ConnectorCreateDialog.kt          # Create new connector
```

---

### 2.2 Timestamp-based Message Seeking

**Priority:** 🟡 Medium
**Effort:** Medium (2-3 days)
**Reference:** Conduktor

#### Feature Description
- Seek to specific timestamp when starting consumption
- Time-range filtering for messages
- Show message timestamps in local/UTC timezone option

#### Implementation Plan

**Add to ConsumerPort:**
```kotlin
suspend fun seekToTimestamp(topic: String, partition: Int?, timestamp: Instant): Result<Unit>
suspend fun getOffsetsForTimestamp(topic: String, timestamp: Instant): Result<Map<Int, Long>>
```

**UI Additions:**
- Add "Start From" dropdown in consumer controls: Latest / Earliest / Timestamp
- Date-time picker for custom timestamp
- "Jump to Time" button in message toolbar

---

### 2.3 Broker/Cluster Health Dashboard

**Priority:** 🟡 Medium
**Effort:** Medium (3-4 days)
**Reference:** Kafka UI, AKHQ

#### Feature Description
- View all brokers in cluster
- Show broker details (ID, host, port, rack)
- Display cluster-level metrics
- Show under-replicated partitions
- Controller information

#### Implementation Plan

**New Models:**
```kotlin
data class BrokerInfo(
    val id: Int,
    val host: String,
    val port: Int,
    val rack: String?,
    val configs: Map<String, String>
)

data class ClusterHealth(
    val controllerId: Int,
    val brokerCount: Int,
    val topicCount: Int,
    val partitionCount: Int,
    val underReplicatedPartitions: Int,
    val offlinePartitions: Int
)
```

**UI Components:**
```
ui/src/main/kotlin/com/lightkafka/ui/
├── ClusterHealthPane.kt              # Overview dashboard
└── BrokerDetailPane.kt               # Individual broker info
```

---

### 2.4 ACL Management

**Priority:** 🟡 Medium
**Effort:** Medium (3-4 days)
**Reference:** Conduktor

#### Feature Description
- List ACLs
- Create/delete ACLs
- Filter by principal, resource, operation

#### Implementation Plan

**Core Models:**
```kotlin
data class AclInfo(
    val principal: String,
    val host: String,
    val operation: AclOperation,
    val permission: AclPermission,     // ALLOW, DENY
    val resource: AclResource
)

data class AclResource(
    val type: ResourceType,            // TOPIC, GROUP, CLUSTER, etc.
    val name: String,
    val patternType: PatternType       // LITERAL, PREFIXED
)
```

**Port Interface:**
```kotlin
interface AclPort {
    suspend fun listAcls(filter: AclFilter?): Result<List<AclInfo>>
    suspend fun createAcl(acl: AclInfo): Result<Unit>
    suspend fun deleteAcl(acl: AclInfo): Result<Unit>
}
```

---

### 2.5 Dead Letter Queue Viewer

**Priority:** 🟡 Medium
**Effort:** Small (1-2 days)
**Reference:** Conduktor

#### Feature Description
- Auto-detect DLQ topics (pattern: `<original-topic>-dlq` or `-error`)
- Dedicated DLQ section in UI
- Show original topic and error details from headers

#### Implementation Plan

**Detection Logic:**
```kotlin
fun isDlqTopic(topic: String): Boolean {
    return topic.endsWith("-dlq") ||
           topic.endsWith("-error") ||
           topic.endsWith(".DLT") ||
           topic.contains("-dead-letter-")
}
```

**UI:**
- Group DLQ topics separately in sidebar
- Show error message from message headers
- Link back to original topic

---

### 2.6 Batch Message Production

**Priority:** 🟡 Medium
**Effort:** Medium (2-3 days)
**Reference:** Offset Explorer

#### Feature Description
- Import messages from file (JSON, CSV)
- Produce multiple messages at once
- Template variables for generated data

#### Implementation Plan

**New Models:**
```kotlin
data class BatchProduceRequest(
    val topic: String,
    val messages: List<MessageToProduce>,
    val delayBetweenMs: Long = 0
)

data class MessageToProduce(
    val key: String?,
    val value: String,
    val headers: Map<String, String>?,
    val partition: Int?
}
```

**UI:**
- "Batch Produce" button in ProducerDialog
- File upload with preview
- Field mapping for CSV

---

## 3. Lower Priority Features

### 3.1 KSQL/ksqlDB Integration

**Priority:** 🟢 Low
**Effort:** Large (7+ days)

**Description:** Execute streaming queries, view streams/tables
**UI:** Query editor with results table

### 3.2 Consumer Group Simulation

**Priority:** 🟢 Low
**Effort:** Medium (2-3 days)

**Description:** Consume messages as if part of a specific consumer group
**UI:** Option in consumer settings to specify group ID

### 3.3 Topic Metrics Dashboard

**Priority:** 🟢 Low
**Effort:** Large (5+ days)

**Description:** Charts for bytes/sec, messages/sec, lag over time
**UI:** Add charting library (lets-plot or similar)

### 3.4 Message Diff/Comparison

**Priority:** 🟢 Low
**Effort:** Small (1-2 days)

**Description:** Select two messages and view side-by-side diff
**UI:** Multi-select in messages table, diff viewer

### 3.5 Bookmarks/Favorites

**Priority:** 🟢 Low
**Effort:** Small (1 day)

**Description:** Mark frequently used topics for quick access
**Storage:** Add to profiles JSON

### 3.6 Custom Deserializers Plugin System

**Priority:** 🟢 Low
**Effort:** Large (7+ days)

**Description:** Allow users to register custom deserializers
**Implementation:** Plugin JAR loading mechanism

---

## 4. Technical Improvements

### 4.1 Performance: Virtualized Message List

**Current Issue:** Loading thousands of messages causes performance issues
**Solution:** Implement lazy loading with Compose `LazyColumn`

```kotlin
// Current approach loads all into memory
// New approach:
LazyColumn {
    items(
        items = messages,
        key = { it.id }
    ) { message ->
        MessageRow(message)
    }
}
```

### 4.2 Connection Resilience

**Current Issue:** Connection failures require manual reconnection
**Solution:** Implement retry with exponential backoff

```kotlin
class ResilientKafkaClient(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 1000,
    private val maxDelayMs: Long = 30000
) {
    suspend fun <T> withRetry(block: suspend () -> T): Result<T> {
        var currentDelay = initialDelayMs
        repeat(maxRetries) { attempt ->
            try {
                return Result.success(block())
            } catch (e: RetriableException) {
                if (attempt == maxRetries - 1) return Result.failure(e)
                delay(currentDelay)
                currentDelay = minOf(currentDelay * 2, maxDelayMs)
            }
        }
        return Result.failure(MaxRetriesExceededException())
    }
}
```

### 4.3 Enhanced Serialization Support

**Current:** JSON and String only
**Improvement:** Add native Avro, Protobuf, MessagePack

```kotlin
interface MessageDeserializer {
    fun deserialize(data: ByteArray): Result<String>
    fun contentType(): String
}

class AvroDeserializer(
    private val schema: Schema
) : MessageDeserializer {
    override fun deserialize(data: ByteArray): Result<String> {
        // Decode Avro binary to JSON string
    }
    override fun contentType() = "application/avro"
}
```

### 4.4 Security Configuration Wizard

**Current:** Security config exists but UI is basic
**Improvement:** Step-by-step wizard for SSL/SASL setup

```
Wizard Steps:
1. Select Security Protocol (PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL)
2. If SSL → Upload or select truststore/keystore files
3. If SASL → Select mechanism, enter credentials
4. Test connection
5. Save profile
```

---

## 5. Recommended Implementation Order

### Phase 1: Foundation (Weeks 1-2)
1. ✅ **Topic Delete and Enhanced Management** - IMPLEMENTED
2. ✅ **Topic Configuration Management** - IMPLEMENTED
3. ✅ **Consumer Groups Management** - IMPLEMENTED

### Phase 2: Integration (Weeks 3-4)
4. ⬜ Schema Registry Integration
5. ⬜ Timestamp-based Message Seeking
6. ⬜ Broker/Cluster Health Dashboard

### Phase 3: Extended Features (Weeks 5-6)
7. ⬜ Kafka Connect UI
8. ⬜ Batch Message Production
9. ⬜ Dead Letter Queue Viewer

### Phase 4: Polish (Weeks 7-8)
10. ⬜ ACL Management
11. ⬜ Performance optimizations
12. ⬜ Security Configuration Wizard

### Phase 5: Advanced (Future)
- KSQL/ksqlDB Integration
- Topic Metrics Dashboard
- Custom Deserializers Plugin System

---

## Appendix: File Structure Overview

```
kafi-kafi/
├── core-kafka/                    # Existing
│   └── src/main/kotlin/com/lightkafka/core/kafka/
│       ├── KafkaAdminService.kt
│       ├── KafkaClientAdapters.kt
│       └── KafkaClientPorts.kt
│
├── schema-registry/               # NEW MODULE
│   └── src/main/kotlin/com/lightkafka/schemaregistry/
│       ├── SchemaRegistryClient.kt
│       └── model/
│
├── kafka-connect/                 # NEW MODULE
│   └── src/main/kotlin/com/lightkafka/kafkaconnect/
│       ├── KafkaConnectClient.kt
│       └── model/
│
└── ui/
    └── src/main/kotlin/com/lightkafka/ui/
        ├── consumer/              # NEW PACKAGE
        │   ├── ConsumerGroupsPane.kt
        │   └── OffsetResetDialog.kt
        ├── topics/                # NEW PACKAGE
        │   ├── TopicConfigDialog.kt
        │   └── TopicPartitionsDialog.kt
        ├── schema/                # NEW PACKAGE
        │   ├── SchemaRegistryPane.kt
        │   └── SchemaDetailPane.kt
        ├── connect/               # NEW PACKAGE
        │   ├── ConnectPane.kt
        │   └── ConnectorDetailPane.kt
        └── cluster/               # NEW PACKAGE
            ├── ClusterHealthPane.kt
            └── BrokerDetailPane.kt
```

---

## References

- **Conduktor:** https://conduktor.io - Feature-rich commercial Kafka GUI
- **Offset Explorer:** https://www.kafkatool.com - Freemium desktop tool
- **Kafka UI (Provectus):** https://github.com/provectus/kafka-ui - Open source web UI
- **AKHQ:** https://github.com/tchiotludo/akhq - Open source web UI
- **Confluent Schema Registry:** https://docs.confluent.io/platform/current/schema-registry/
- **Kafka AdminClient API:** https://kafka.apache.org/documentation/#adminapi
