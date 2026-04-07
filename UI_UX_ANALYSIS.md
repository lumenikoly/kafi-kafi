# UI/UX Analysis: Connection Management Flow

> Analysis date: 2026-03-10

---

## Current Flow Analysis

### Adding a New Broker - Current Steps

```
1. Click "Connections" button in top bar
2. Dialog opens (960x640px)
3. Click "+" button in left sidebar to create new profile
4. Enter profile name
5. Enter bootstrap servers (comma separated)
6. Click "Test" to verify connection
7. Click "Save Connection"
8. Profile becomes active automatically
```

### Current UI Components

| Component | Location | Purpose |
|-----------|----------|---------|
| Profile Switcher | Top bar | Quick profile selection |
| Connections Button | Top bar | Opens connection manager |
| Profile List Pane | Dialog left sidebar | List all profiles |
| Connection Editor Pane | Dialog right area | Edit profile details |

---

## Issues Identified

### 🔴 Critical Issues

#### 1. Security Configuration Not Exposed in UI

**Problem:** The `ClusterProfile` model supports rich security options, but the UI only shows 2 fields.

**Model supports:**
```kotlin
data class ClusterProfile(
    val name: String,
    val bootstrapServers: List<String>,
    val securityProtocol: SecurityProtocol,  // ❌ NOT IN UI
    val sasl: SaslConfig?,                    // ❌ NOT IN UI
    val ssl: SslConfig?,                      // ❌ NOT IN UI
    val clientId: String?,                    // ❌ NOT IN UI
    val additionalProperties: Map<String, String>, // ❌ NOT IN UI
)
```

**UI shows:**
- Profile name
- Bootstrap servers

**Impact:** Users cannot connect to secured Kafka clusters (SASL, SSL, mTLS) through the UI.

---

#### 2. No Visual Connection Status on Profiles

**Problem:** Profile cards show no indication of whether the connection is healthy.

**Current profile card:**
```
┌─────────────────────────────┐
│ ● Production Cluster        │  ← No status indicator
│   broker1:9092,broker2:9092 │
└─────────────────────────────┘
```

**Expected:**
```
┌─────────────────────────────┐
│ 🟢 Production Cluster       │  ← Green = connected
│   broker1:9092,broker2:9092 │
│   Last tested: 2 min ago    │
└─────────────────────────────┘
```

---

#### 3. No Delete Confirmation

**Problem:** Deleting a profile happens immediately without confirmation.

**Current flow:**
```
Click Delete → Profile deleted immediately
```

**Expected flow:**
```
Click Delete → Confirmation dialog → Confirm → Profile deleted
```

---

### 🟡 Medium Issues

#### 4. Duplicate Test Buttons

**Problem:** Test button exists in both sidebar and editor pane, causing confusion.

**Locations:**
- Sidebar: "Test" button (tests selected profile)
- Editor pane: "Test" button (tests current form values)

**Issue:** Users don't know which one to use. The sidebar one tests saved values, the editor one tests unsaved values.

---

#### 5. No Profile Duplication

**Problem:** Users cannot clone an existing profile.

**Use case:** Creating a staging profile based on production settings.

---

#### 6. Basic Test Status Feedback

**Problem:** Test results are plain text messages without structure.

**Current:**
```
┌─────────────────────────────────────────┐
│ Connection test passed. Found 5 topics. │  ← Plain text
└─────────────────────────────────────────┘
```

**Expected:**
```
┌─────────────────────────────────────────┐
│ ✅ Connection Successful                │
│                                         │
│ Cluster ID: abc123                      │
│ Brokers: 3 available                    │
│ Topics: 24 (2 internal)                 │
│ Controller: broker-2:9092               │
│ Latency: 45ms                           │
└─────────────────────────────────────────┘
```

---

#### 7. No Empty State Guidance

**Problem:** When no profiles exist, the dialog shows an empty list with no guidance.

**Current:**
```
┌─────────────┬──────────────────────────┐
│ Profiles  + │ Connection Settings      │
│             │                          │
│  (empty)    │ [Profile name________]   │
│             │ [Bootstrap servers____]  │
│             │                          │
└─────────────┴──────────────────────────┘
```

**Expected:**
```
┌─────────────┬──────────────────────────┐
│ Profiles  + │ Welcome!                 │
│             │                          │
│  (empty)    │ Let's connect to your    │
│             │ first Kafka cluster.     │
│             │                          │
│             │ ┌──────────────────────┐ │
│             │ │ 🚀 Quick Connect     │ │
│             │ │   localhost:9092     │ │
│             │ └──────────────────────┘ │
│             │                          │
│             │ ┌──────────────────────┐ │
│             │ │ 🔐 Secure Cluster    │ │
│             │ │   SASL/SSL config    │ │
│             │ └──────────────────────┘ │
└─────────────┴──────────────────────────┘
```

---

### 🟢 Minor Issues

#### 8. Profile Switcher Limited Information

**Problem:** Top bar profile dropdown only shows name, no connection health.

**Current:**
```
[🟢 Production Cluster ▾]
```

**Expected:**
```
[🟢 Production Cluster (3 brokers) ▾]
```

---

#### 9. No Profile Import/Export

**Problem:** Users cannot share profiles between team members.

---

#### 10. No "Connect on Startup" Option

**Problem:** Users must manually select a profile every time the app starts.

---

## Improvement Plan

### Phase 1: Security Configuration UI

Add security configuration options to the connection editor.

#### New UI Structure

```
┌─────────────────────────────────────────────────────────────────┐
│ Connection Settings                                    [Close] │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ ┌─ Basic ─────────────────────────────────────────────────────┐│
│ │ Profile name     [Production Cluster________________]       ││
│ │ Bootstrap servers [kafka-1:9092,kafka-2:9092________]       ││
│ │ Client ID        [light-kafka-viewer___________] (optional) ││
│ └─────────────────────────────────────────────────────────────┘│
│                                                                 │
│ ┌─ Security ──────────────────────────────────────────────────┐│
│ │ Protocol  [PLAINTEXT ▾]  [SSL]  [SASL_PLAINTEXT]  [SASL_SSL]││
│ │                                                              ││
│ │ ┌─ SASL (when SASL_* selected) ────────────────────────────┐││
│ │ │ Mechanism [PLAIN ▾] [SCRAM-SHA-256] [SCRAM-SHA-512] [OA] │││
│ │ │ Username  [app_user________________________]             │││
│ │ │ Password  [••••••••••••] [👁]                            │││
│ │ └──────────────────────────────────────────────────────────┘││
│ │                                                              ││
│ │ ┌─ SSL (when SSL or SASL_SSL selected) ────────────────────┐││
│ │ │ Truststore     [JKS] [/path/to/truststore.jks    ] [📁]  │││
│ │ │ Truststore PW  [••••••••••]                              │││
│ │ │ Keystore       [JKS] [/path/to/keystore.jks      ] [📁]  │││
│ │ │ Keystore PW    [••••••••••]                              │││
│ │ │ Key Password   [••••••••••]                              │││
│ │ └──────────────────────────────────────────────────────────┘││
│ └─────────────────────────────────────────────────────────────┘│
│                                                                 │
│ ┌─ Advanced ───────────────────────────────────────────────────┐│
│ │ [+ Add Property]                                             ││
│ │ ┌──────────────────────────────────────────────────────────┐ ││
│ │ │ fetch.max.bytes    [52428800]                      [×]   │ ││
│ │ │ max.poll.records   [500]                            [×]   │ ││
│ │ └──────────────────────────────────────────────────────────┘ ││
│ └─────────────────────────────────────────────────────────────┘│
│                                                                 │
│ [Test Connection] [Save] [Delete]                               │
│                                                                 │
│ ┌─ Test Results ──────────────────────────────────────────────┐│
│ │ ✅ Connected successfully                                    ││
│ │ Cluster: my-kafka-cluster (3 brokers)                       ││
│ │ Controller: kafka-2:9092                                    ││
│ │ Latency: 23ms                                                ││
│ └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

#### Implementation

**New file:** `ui/src/main/kotlin/com/lightkafka/ui/ConnectionEditorState.kt`

```kotlin
data class ConnectionEditorState(
    // Basic
    val name: String = "",
    val bootstrapServers: String = "localhost:9092",
    val clientId: String = "",

    // Security
    val securityProtocol: SecurityProtocol = SecurityProtocol.PLAINTEXT,
    val saslMechanism: SaslMechanism = SaslMechanism.PLAIN,
    val saslUsername: String = "",
    val saslPassword: String = "",

    // SSL
    val truststorePath: String = "",
    val truststorePassword: String = "",
    val keystorePath: String = "",
    val keystorePassword: String = "",
    val keyPassword: String = "",

    // Advanced
    val additionalProperties: Map<String, String> = emptyMap(),

    // UI State
    val testStatus: ConnectionTestStatus? = null,
    val isTesting: Boolean = false,
    val isNewProfile: Boolean = true,
)

sealed class ConnectionTestStatus {
    data class Success(
        val clusterId: String,
        val brokerCount: Int,
        val controllerId: String,
        val topicCount: Int,
        val latencyMs: Long,
    ) : ConnectionTestStatus()

    data class Failure(
        val error: String,
        val suggestion: String? = null,
    ) : ConnectionTestStatus()

    data class InProgress(val message: String = "Testing...") : ConnectionTestStatus()
}
```

---

### Phase 2: Enhanced Profile Cards

Update profile cards to show connection status and metadata.

```kotlin
@Composable
private fun profileCard(
    profile: ClusterProfile,
    selected: Boolean,
    connectionStatus: ConnectionStatus?,
    onClick: () -> Unit,
) {
    // ... existing code ...

    Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        when (connectionStatus?.state) {
                            ConnectionState.CONNECTED -> StatusSuccess
                            ConnectionState.ERROR -> StatusError
                            ConnectionState.UNKNOWN -> StatusWarning
                            null -> TextMuted
                        },
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = profile.name,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) AccentViolet else TextPrimary,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = profile.bootstrapServers.joinToString(","),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            maxLines = 1,
        )

        // Status info
        connectionStatus?.let { status ->
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (status.state) {
                    ConnectionState.CONNECTED -> {
                        Text(
                            text = "${status.brokerCount} brokers • ${status.latencyMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusSuccess.copy(alpha = 0.8f),
                        )
                    }
                    ConnectionState.ERROR -> {
                        Text(
                            text = "Connection failed",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusError.copy(alpha = 0.8f),
                        )
                    }
                    ConnectionState.UNKNOWN -> {
                        Text(
                            text = "Not tested",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                }
            }
        }
    }
}
```

---

### Phase 3: Improved User Flow

#### New Connection Wizard

For first-time users or complex configurations, offer a wizard:

```
Step 1: Choose Connection Type
┌─────────────────────────────────────────────────────────┐
│ How do you want to connect?                              │
│                                                          │
│ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────┐ │
│ │ 🚀 Local        │ │ ☁️ Cloud        │ │ 🔐 Secured  │ │
│ │                 │ │                 │ │             │ │
│ │ localhost:9092  │ │ Confluent, MSK  │ │ SASL/SSL    │ │
│ │ No auth         │ │ or similar      │ │ required    │ │
│ └─────────────────┘ └─────────────────┘ └─────────────┘ │
│                                                          │
│ ┌─────────────────┐                                      │
│ │ 📦 Import       │                                      │
│ │                 │                                      │
│ │ From file or    │                                      │
│ │ connection str  │                                      │
│ └─────────────────┘                                      │
└─────────────────────────────────────────────────────────┘
```

```
Step 2: Enter Details (for Cloud/Secured)
┌─────────────────────────────────────────────────────────┐
│ Connection Details                                       │
│                                                          │
│ Profile name                                             │
│ [My Confluent Cluster___________________]               │
│                                                          │
│ Bootstrap servers                                        │
│ [pkc-xyz.us-east-1.aws.confluent.cloud:9092]           │
│                                                          │
│ Authentication                                          │
│ ○ API Key (Confluent)                                   │
│ ● SASL/SSL                                               │
│ ○ mTLS                                                   │
│                                                          │
│ ┌─ SASL Credentials ───────────────────────────────────┐│
│ │ API Key / Username                                    ││
│ │ [_________________________________]                   ││
│ │                                                       ││
│ │ API Secret / Password                                 ││
│ │ [•••••••••••••••••••••••••] [👁]                      ││
│ └───────────────────────────────────────────────────────┘│
│                                                          │
│                                    [Back] [Test & Continue]│
└─────────────────────────────────────────────────────────┘
```

---

### Phase 4: Quick Actions

Add quick actions to profile cards:

```kotlin
@Composable
private fun profileCardWithActions(
    profile: ClusterProfile,
    // ...
) {
    var showActions by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .onPointerEvent(PointerEventType.Enter) { showActions = true }
            .onPointerEvent(PointerEventType.Exit) { showActions = false }
    ) {
        Row {
            // ... profile info ...

            // Quick actions (shown on hover)
            AnimatedVisibility(visible = showActions) {
                Row {
                    IconButton(onClick = { /* duplicate */ }) {
                        Icon(Icons.Default.ContentCopy, "Duplicate")
                    }
                    IconButton(onClick = { /* test */ }) {
                        Icon(Icons.Default.PlayArrow, "Test")
                    }
                }
            }
        }
    }
}
```

---

### Phase 5: Delete Confirmation

```kotlin
@Composable
private fun deleteConfirmationDialog(
    profile: ClusterProfile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, "Warning", tint = StatusError) },
        title = { Text("Delete Connection Profile?") },
        text = {
            Column {
                Text("Are you sure you want to delete this connection profile?")
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = profile.name,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = profile.bootstrapServers.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "This action cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusError
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StatusError)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

---

## Implementation Priority

| Priority | Feature | Effort | Impact |
|----------|---------|--------|--------|
| 1 | Security configuration UI | 3-4 days | Critical - enables enterprise use |
| 2 | Connection status on cards | 1 day | High - improves visibility |
| 3 | Delete confirmation | 0.5 day | High - prevents accidents |
| 4 | Enhanced test feedback | 1 day | Medium - better UX |
| 5 | Profile duplication | 0.5 day | Medium - convenience |
| 6 | Empty state guidance | 0.5 day | Medium - first-time UX |
| 7 | Connection wizard | 2-3 days | Low - nice to have |
| 8 | Import/Export profiles | 1 day | Low - team sharing |

---

## Summary

### Key Improvements

1. **Expose all security options** in the UI (SASL, SSL, mTLS)
2. **Show connection health** on profile cards and switcher
3. **Add delete confirmation** to prevent accidental deletions
4. **Improve test feedback** with structured results
5. **Add quick actions** (duplicate, test) on profile cards
6. **Guide first-time users** with empty state and wizard

### Expected Outcomes

- Users can connect to **any Kafka cluster** (not just plaintext)
- **Reduced support burden** for connection issues
- **Better visibility** into cluster health
- **Fewer accidental deletions**
- **Improved first-time user experience**
