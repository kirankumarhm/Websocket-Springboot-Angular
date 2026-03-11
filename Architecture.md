# WebSocket Device Dashboard - Complete Architecture

## System Overview

Real-time device monitoring system using Spring Boot WebSocket backend and Angular frontend with support for large payloads, FCM integration, and MongoDB persistence.

---

## High-Level Architecture

```mermaid 
graph TB
    subgraph "Frontend - Angular 20"
        UI[User Interface]
        WS_CLIENT[WebSocket Client<br/>STOMP/SockJS]
        HTTP_CLIENT[HTTP Client]
    end
    
    subgraph "Backend - Spring Boot 3.5.7"
        WS_ENDPOINT[WebSocket Endpoint<br/>/ws]
        REST_API[REST Controllers]
        SERVICES[Business Services]
        WS_BROKER[Message Broker<br/>/topic]
    end
    
    subgraph "Data Layer"
        MONGO[(MongoDB<br/>devicedb)]
    end
    
    subgraph "External Services"
        FCM[FCM Service<br/>Mock]
    end
    
    UI -->|Search Device| HTTP_CLIENT
    HTTP_CLIENT -->|HTTP POST| REST_API
    REST_API -->|Trigger| FCM
    FCM -->|Mock Response| SERVICES
    SERVICES -->|Save| MONGO
    SERVICES -->|Notify| WS_BROKER
    WS_BROKER -->|Push Update| WS_ENDPOINT
    WS_ENDPOINT -->|Real-time Data| WS_CLIENT
    WS_CLIENT -->|Update UI| UI
    
    style UI fill:#e1f5ff
    style MONGO fill:#4caf50
    style FCM fill:#ff9800
    style WS_BROKER fill:#9c27b0
```

---

## Component Architecture

```mermaid
graph TB
    subgraph "Presentation Layer"
        APP[App Component<br/>app.ts]
        TEMPLATE[HTML Template<br/>app.html]
        WS_SERVICE[WebSocket Service<br/>websocket.service.ts]
    end
    
    subgraph "API Layer"
        SEARCH_CTRL[SearchController<br/>/api/search-device]
        MOBILE_CTRL[MobileDeviceController<br/>/mobile/telemetry]
    end
    
    subgraph "Service Layer"
        DEVICE_SVC[DeviceService<br/>CRUD + Notifications]
        FCM_SVC[FCMService<br/>Mock FCM]
        PAYLOAD_SVC[LargePayloadService<br/>Compression/Chunking]
    end
    
    subgraph "Configuration Layer"
        WS_CONFIG[WebSocketConfiguration<br/>STOMP Setup]
        PAYLOAD_CONFIG[LargePayloadConfig<br/>HTTP Config]
    end
    
    subgraph "Data Layer"
        DEVICE_REPO[DeviceRepository<br/>MongoDB Interface]
        DEVICE_MODEL[DeviceData Model<br/>@Document]
    end
    
    subgraph "Infrastructure"
        SESSION_MGR[SessionManager<br/>Session Tracking]
        WS_LISTENER[WebSocketEventListener<br/>Disconnect Handler]
    end
    
    APP --> WS_SERVICE
    APP --> SEARCH_CTRL
    SEARCH_CTRL --> FCM_SVC
    FCM_SVC --> DEVICE_SVC
    MOBILE_CTRL --> DEVICE_SVC
    DEVICE_SVC --> PAYLOAD_SVC
    DEVICE_SVC --> DEVICE_REPO
    DEVICE_REPO --> DEVICE_MODEL
    PAYLOAD_SVC --> WS_CONFIG
    WS_LISTENER --> SESSION_MGR
    
    style DEVICE_SVC fill:#2196f3
    style PAYLOAD_SVC fill:#ff5722
    style WS_CONFIG fill:#9c27b0
```

---

## Data Flow Architecture

### 1. Device Search Flow

```mermaid
sequenceDiagram
    participant User
    participant Angular as Angular UI
    participant HTTP as HTTP Client
    participant SearchCtrl as SearchController
    participant FCM as FCMService
    participant DeviceSvc as DeviceService
    participant PayloadSvc as LargePayloadService
    participant MongoDB
    participant WebSocket as WebSocket Broker
    
    User->>Angular: Enter Phone Number
    Angular->>Angular: Subscribe to /topic/device-updates
    Angular->>HTTP: POST /api/search-device
    HTTP->>SearchCtrl: {phoneNumber}
    SearchCtrl->>FCM: sendDeviceInfoRequest()
    
    Note over FCM: Simulate 2s delay
    FCM->>FCM: simulateDeviceResponse()
    FCM->>DeviceSvc: upsert(DeviceData)
    DeviceSvc->>MongoDB: save(device)
    MongoDB-->>DeviceSvc: saved device
    
    DeviceSvc->>PayloadSvc: sendLargePayload()
    
    alt Payload < 100KB
        PayloadSvc->>WebSocket: Send Uncompressed
    else Payload > 100KB
        PayloadSvc->>PayloadSvc: GZIP + Base64
        PayloadSvc->>WebSocket: Send Compressed
    else Payload > 1MB
        PayloadSvc->>PayloadSvc: Split into 500KB chunks
        loop For each chunk
            PayloadSvc->>WebSocket: Send Chunk
        end
    end
    
    WebSocket->>Angular: Push to /topic/device-updates/{phone}
    Angular->>Angular: handleLargePayload()
    Angular->>User: Display Device Data
```

### 2. WebSocket Connection Flow

```mermaid
sequenceDiagram
    participant Angular
    participant SockJS
    participant STOMP
    participant WSEndpoint as WebSocket Endpoint
    participant Broker as Message Broker
    
    Angular->>SockJS: new SockJS('/ws')
    SockJS->>STOMP: Stomp.over(socket)
    STOMP->>WSEndpoint: Connect
    WSEndpoint-->>STOMP: Connection Established
    
    Note over STOMP,Broker: Heartbeat: 10s/20s
    
    Angular->>STOMP: subscribe('/topic/device-updates')
    STOMP->>Broker: Register Subscription
    
    Note over Broker: Device Update Event
    
    Broker->>STOMP: Push Message
    STOMP->>Angular: Message Received
    Angular->>Angular: Parse & Display
    
    Note over Angular,STOMP: Connection Health Check (30s)
    
    alt Connection Lost
        Angular->>Angular: Detect Disconnect
        Angular->>SockJS: Reconnect
        SockJS->>STOMP: Re-establish
        STOMP->>Broker: Re-subscribe
    end
```

---

## Payload Processing Architecture

```mermaid
graph TB
    subgraph "Payload Decision Tree"
        START[Device Data]
        CHECK_SIZE{Size Check}
        REGULAR[Regular Payload<br/>< 100KB]
        COMPRESSED[Compressed Payload<br/>100KB - 1MB]
        CHUNKED[Chunked Payload<br/>> 1MB]
    end
    
    subgraph "Backend Processing"
        COMPRESS[GZIP Compression<br/>+ Base64 Encode]
        CHUNK[Split into 500KB<br/>Chunks with SessionID]
        SEND_REGULAR[Send Direct JSON]
        SEND_COMPRESSED[Send Compressed]
        SEND_CHUNKS[Send Multiple Chunks]
    end
    
    subgraph "Frontend Processing"
        RECEIVE[Receive Message]
        CHECK_TYPE{Message Type?}
        DECOMPRESS[Base64 Decode<br/>+ GZIP Decompress]
        RECONSTRUCT[Buffer & Reconstruct<br/>from Chunks]
        PARSE[Parse JSON]
        DISPLAY[Update UI]
    end
    
    START --> CHECK_SIZE
    CHECK_SIZE -->|< 100KB| REGULAR
    CHECK_SIZE -->|100KB-1MB| COMPRESSED
    CHECK_SIZE -->|> 1MB| CHUNKED
    
    REGULAR --> SEND_REGULAR
    COMPRESSED --> COMPRESS --> SEND_COMPRESSED
    CHUNKED --> CHUNK --> SEND_CHUNKS
    
    SEND_REGULAR --> RECEIVE
    SEND_COMPRESSED --> RECEIVE
    SEND_CHUNKS --> RECEIVE
    
    RECEIVE --> CHECK_TYPE
    CHECK_TYPE -->|Regular| PARSE
    CHECK_TYPE -->|Compressed| DECOMPRESS --> PARSE
    CHECK_TYPE -->|Chunked| RECONSTRUCT --> PARSE
    
    PARSE --> DISPLAY
    
    style COMPRESS fill:#ff9800
    style CHUNK fill:#f44336
    style DECOMPRESS fill:#ff9800
    style RECONSTRUCT fill:#f44336
```

---

## Database Schema

```mermaid
erDiagram
    DEVICES {
        string _id PK
        string deviceId UK
        string phoneNumber
        string wifiStatus
        int batteryLevel
        string storageUsed
        string signalStrength
        string model
        string firmware
        string imei
    }
    
    DEVICES ||--o{ WEBSOCKET_SESSIONS : tracks
    
    WEBSOCKET_SESSIONS {
        string sessionId PK
        string phoneNumber FK
        timestamp connectedAt
        timestamp disconnectedAt
    }
```

---

## Technology Stack

```mermaid
graph LR
    subgraph "Frontend Stack"
        A1[Angular 20]
        A2[TypeScript 5.9]
        A3[STOMP.js 7.2]
        A4[SockJS 1.6]
        A5[RxJS 7.8]
    end
    
    subgraph "Backend Stack"
        B1[Spring Boot 3.5.7]
        B2[Java 21]
        B3[Spring WebSocket]
        B4[Spring Data MongoDB]
        B5[Jackson JSON]
    end
    
    subgraph "Infrastructure"
        C1[MongoDB 7.x]
        C2[Maven]
        C3[Tomcat Embedded]
    end
    
    A1 --> A2
    A1 --> A3
    A3 --> A4
    A1 --> A5
    
    B1 --> B2
    B1 --> B3
    B1 --> B4
    B1 --> B5
    
    B1 --> C3
    B4 --> C1
    B1 --> C2
    
    style A1 fill:#dd0031
    style B1 fill:#6db33f
    style C1 fill:#4caf50
```

---

## WebSocket Configuration Details

```mermaid
graph TB
    subgraph "WebSocket Configuration"
        ENDPOINT[STOMP Endpoint: /ws]
        BROKER[Message Broker: /topic]
        APP_PREFIX[App Prefix: /app]
    end
    
    subgraph "Transport Settings"
        MSG_SIZE[Max Message: 1MB]
        BUFFER[Send Buffer: 2MB]
        TIMEOUT[Timeout: 60s]
        HEARTBEAT[Heartbeat: 10s/20s]
    end
    
    subgraph "SockJS Settings"
        STREAM[Stream Limit: 1MB]
        CACHE[Cache Size: 2000]
        DISCONNECT[Disconnect Delay: 30s]
    end
    
    subgraph "Thread Pool"
        POOL_SIZE[Pool Size: 50]
        SCHEDULER[Task Scheduler]
        POLICY[Caller Runs Policy]
    end
    
    ENDPOINT --> MSG_SIZE
    ENDPOINT --> BUFFER
    ENDPOINT --> TIMEOUT
    BROKER --> HEARTBEAT
    ENDPOINT --> STREAM
    ENDPOINT --> CACHE
    ENDPOINT --> DISCONNECT
    BROKER --> SCHEDULER
    SCHEDULER --> POOL_SIZE
    SCHEDULER --> POLICY
    
    style ENDPOINT fill:#9c27b0
    style BROKER fill:#673ab7
    style SCHEDULER fill:#3f51b5
```

---

## REST API Endpoints

```mermaid
graph LR
    subgraph "Search API"
        S1[POST /api/search-device]
    end
    
    subgraph "Mobile Device API"
        M1[POST /mobile/telemetry]
        M2[POST /mobile/fcm-response/:deviceId]
    end
    
    subgraph "Debug API"
        D1[GET /api/debug/devices]
    end
    
    S1 -->|Trigger FCM| FCM_SERVICE[FCM Service]
    M1 -->|Upsert Device| DEVICE_SERVICE[Device Service]
    M2 -->|Update Device| DEVICE_SERVICE
    D1 -->|List All| DEVICE_SERVICE
    
    FCM_SERVICE --> DEVICE_SERVICE
    DEVICE_SERVICE --> MONGODB[(MongoDB)]
    DEVICE_SERVICE --> WEBSOCKET[WebSocket Broker]
    
    style S1 fill:#2196f3
    style M1 fill:#4caf50
    style M2 fill:#4caf50
    style D1 fill:#ff9800
```

---

## WebSocket Topics

```mermaid
graph TB
    subgraph "Topic Structure"
        ROOT[/topic]
        GENERAL[/topic/device-updates]
        PHONE[/topic/device-updates/:phoneNumber]
    end
    
    subgraph "Message Types"
        REGULAR[Regular Message<br/>compressed: false]
        COMPRESSED[Compressed Message<br/>compressed: true]
        CHUNKED[Chunked Message<br/>chunked: true]
    end
    
    ROOT --> GENERAL
    ROOT --> PHONE
    
    GENERAL --> REGULAR
    PHONE --> REGULAR
    PHONE --> COMPRESSED
    PHONE --> CHUNKED
    
    style PHONE fill:#2196f3
    style COMPRESSED fill:#ff9800
    style CHUNKED fill:#f44336
```

---

## Message Formats

### Regular Message
```json
{
  "compressed": false,
  "data": {
    "deviceId": "DEVICE_001",
    "phoneNumber": "1234567890",
    "wifiStatus": "Connected",
    "batteryLevel": 85,
    "storageUsed": "32",
    "signalStrength": "Excellent",
    "model": "iPhone 14",
    "firmware": "iOS 17.1",
    "imei": "IMEI_123456789"
  },
  "timestamp": 1234567890000
}
```

### Compressed Message
```json
{
  "compressed": true,
  "data": "H4sIAAAAAAAA/6tWKkktLlGyUlAqS8wpTtVRKi1OLUpV0lFQSixOTc...",
  "originalSize": 150000,
  "compressedSize": 45000,
  "timestamp": 1234567890000
}
```

### Chunked Message
```json
{
  "chunked": true,
  "sessionId": "session_1234567890_456",
  "chunkIndex": 0,
  "totalChunks": 5,
  "data": "chunk-data-here...",
  "timestamp": 1234567890000
}
```

---

## Service Layer Architecture

```mermaid
graph TB
    subgraph "DeviceService"
        DS1[findAll]
        DS2[findByDeviceId]
        DS3[save]
        DS4[upsert]
        DS5[deleteByDeviceId]
        DS6[notifyFrontend]
    end
    
    subgraph "FCMService"
        FS1[sendDeviceInfoRequest]
        FS2[simulateDeviceResponse]
        FS3[Rate Limiting<br/>Semaphore: 50]
    end
    
    subgraph "LargePayloadService"
        LS1[sendLargePayload]
        LS2[sendChunkedPayload]
        LS3[compressData]
        LS4[decompressData]
        LS5[sendToPhoneSpecificTopic]
    end
    
    DS4 --> DS3
    DS3 --> DS6
    DS6 --> LS1
    LS1 --> LS3
    LS1 --> LS5
    LS2 --> LS5
    
    FS1 --> FS3
    FS1 --> FS2
    FS2 --> DS4
    
    style DS4 fill:#2196f3
    style FS1 fill:#ff9800
    style LS1 fill:#f44336
```

---

## Frontend State Management

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Connecting: User enters phone
    Connecting --> Subscribed: WebSocket connected
    Subscribed --> Searching: HTTP POST sent
    Searching --> Receiving: FCM triggered
    Receiving --> Processing: Message received
    
    Processing --> DisplayRegular: Regular payload
    Processing --> DisplayCompressed: Compressed payload
    Processing --> BufferingChunks: Chunked payload
    
    BufferingChunks --> BufferingChunks: More chunks
    BufferingChunks --> Reconstructing: All chunks received
    Reconstructing --> DisplayChunked: Reconstruction complete
    
    DisplayRegular --> Idle: Session ended
    DisplayCompressed --> Idle: Session ended
    DisplayChunked --> Idle: Session ended
    
    Connecting --> Reconnecting: Connection failed
    Reconnecting --> Connecting: Retry
    Subscribed --> Reconnecting: Connection lost
```

---

## Error Handling & Resilience

```mermaid
graph TB
    subgraph "Frontend Resilience"
        F1[Connection Health Check<br/>30s interval]
        F2[Auto Reconnect<br/>2s retry]
        F3[Chunk Buffer<br/>Session-based]
        F4[Decompression Fallback]
    end
    
    subgraph "Backend Resilience"
        B1[Rate Limiting<br/>50 concurrent FCM]
        B2[Connection Timeout<br/>60s]
        B3[Thread Pool<br/>50 threads]
        B4[Caller Runs Policy]
    end
    
    subgraph "Database Resilience"
        D1[Connection Pool<br/>10-50 connections]
        D2[Socket Timeout<br/>60s]
        D3[Upsert Strategy]
    end
    
    subgraph "WebSocket Resilience"
        W1[Heartbeat<br/>10s/20s]
        W2[Disconnect Delay<br/>30s]
        W3[Send Buffer<br/>2MB]
        W4[Message Size Limit<br/>1MB]
    end
    
    F1 --> F2
    F3 --> F4
    
    B1 --> B2
    B3 --> B4
    
    D1 --> D2
    D2 --> D3
    
    W1 --> W2
    W3 --> W4
    
    style F2 fill:#4caf50
    style B1 fill:#ff9800
    style D3 fill:#2196f3
    style W1 fill:#9c27b0
```

---

## Performance Optimization

```mermaid
graph LR
    subgraph "Backend Optimizations"
        B1[Async FCM Processing<br/>CompletableFuture]
        B2[MongoDB Pooling<br/>10-50 connections]
        B3[GZIP Compression<br/>>100KB payloads]
        B4[Chunking Strategy<br/>>1MB payloads]
    end
    
    subgraph "Frontend Optimizations"
        F1[Change Detection<br/>Manual trigger]
        F2[Chunk Buffering<br/>Map-based storage]
        F3[Lazy Decompression<br/>On-demand]
        F4[Connection Reuse<br/>Health checks]
    end
    
    subgraph "Network Optimizations"
        N1[SockJS Fallback<br/>Multiple transports]
        N2[Heartbeat Tuning<br/>10s/20s]
        N3[Phone-specific Topics<br/>Targeted delivery]
        N4[Send Buffer<br/>2MB capacity]
    end
    
    B1 --> B2
    B2 --> B3
    B3 --> B4
    
    F1 --> F2
    F2 --> F3
    F3 --> F4
    
    N1 --> N2
    N2 --> N3
    N3 --> N4
    
    style B3 fill:#ff9800
    style B4 fill:#f44336
    style F2 fill:#2196f3
    style N3 fill:#9c27b0
```

---

## Deployment Architecture

```mermaid
graph TB
    subgraph "Client Tier"
        BROWSER[Web Browser<br/>Angular App<br/>Port: 4200]
    end
    
    subgraph "Application Tier"
        SPRING[Spring Boot<br/>Embedded Tomcat<br/>Port: 8080]
        WS_SERVER[WebSocket Server<br/>STOMP/SockJS]
    end
    
    subgraph "Data Tier"
        MONGO_SERVER[MongoDB Server<br/>Port: 27017<br/>Database: devicedb]
    end
    
    BROWSER -->|HTTP/REST| SPRING
    BROWSER -->|WebSocket| WS_SERVER
    SPRING --> MONGO_SERVER
    WS_SERVER -.->|Embedded in| SPRING
    
    style BROWSER fill:#e1f5ff
    style SPRING fill:#6db33f
    style MONGO_SERVER fill:#4caf50
```

---

## Security Considerations

```mermaid
graph TB
    subgraph "CORS Configuration"
        C1[Allowed Origin<br/>http://localhost:4200]
        C2[Allowed Methods<br/>GET, POST, PUT, DELETE]
        C3[Allowed Headers<br/>All]
    end
    
    subgraph "WebSocket Security"
        W1[Origin Validation]
        W2[Session Management]
        W3[Disconnect Cleanup]
    end
    
    subgraph "Data Security"
        D1[No PII in Logs]
        D2[Compression Encryption]
        D3[Session-based Chunking]
    end
    
    subgraph "Rate Limiting"
        R1[FCM Semaphore<br/>50 concurrent]
        R2[Connection Timeout<br/>60s]
        R3[Thread Pool Limit<br/>50 threads]
    end
    
    C1 --> W1
    C2 --> W1
    C3 --> W1
    
    W1 --> W2
    W2 --> W3
    
    D1 --> D2
    D2 --> D3
    
    R1 --> R2
    R2 --> R3
    
    style W1 fill:#f44336
    style D2 fill:#ff9800
    style R1 fill:#2196f3
```

---

## Key Design Patterns

### 1. **Repository Pattern**
- `DeviceRepository` extends `MongoRepository`
- Abstraction over data access layer

### 2. **Service Layer Pattern**
- `DeviceService`: Business logic
- `FCMService`: External service integration
- `LargePayloadService`: Payload processing

### 3. **Observer Pattern**
- WebSocket message broker
- Real-time notifications to subscribers

### 4. **Strategy Pattern**
- Payload handling based on size
- Regular / Compressed / Chunked strategies

### 5. **Singleton Pattern**
- Spring beans (services, repositories)
- SessionManager for tracking

### 6. **Factory Pattern**
- WebSocket connection creation
- STOMP client instantiation

---

## Scalability Considerations

```mermaid
graph TB
    subgraph "Horizontal Scaling"
        H1[Load Balancer]
        H2[Multiple Spring Boot Instances]
        H3[Sticky Sessions for WebSocket]
    end
    
    subgraph "Vertical Scaling"
        V1[Thread Pool Size: 50]
        V2[MongoDB Pool: 10-50]
        V3[Buffer Sizes: 1-2MB]
    end
    
    subgraph "Data Scaling"
        D1[MongoDB Sharding]
        D2[Index on deviceId]
        D3[Index on phoneNumber]
    end
    
    subgraph "Message Scaling"
        M1[Phone-specific Topics]
        M2[Compression for Large Data]
        M3[Chunking for Very Large Data]
    end
    
    H1 --> H2
    H2 --> H3
    
    V1 --> V2
    V2 --> V3
    
    D1 --> D2
    D2 --> D3
    
    M1 --> M2
    M2 --> M3
    
    style H3 fill:#9c27b0
    style V1 fill:#2196f3
    style D1 fill:#4caf50
    style M2 fill:#ff9800
```

---

## Monitoring & Logging

```mermaid
graph LR
    subgraph "Application Logs"
        L1[Connection Events]
        L2[FCM Requests]
        L3[Device Updates]
        L4[Payload Processing]
    end
    
    subgraph "WebSocket Metrics"
        W1[Active Connections]
        W2[Message Throughput]
        W3[Disconnect Events]
        W4[Heartbeat Status]
    end
    
    subgraph "Database Metrics"
        D1[Query Performance]
        D2[Connection Pool Usage]
        D3[Document Size]
    end
    
    subgraph "Performance Metrics"
        P1[Compression Ratio]
        P2[Chunk Count]
        P3[Response Time]
        P4[Memory Usage]
    end
    
    L1 --> W1
    L2 --> W2
    L3 --> D1
    L4 --> P1
    
    W3 --> W4
    D2 --> D3
    P2 --> P3
    P3 --> P4
    
    style L1 fill:#2196f3
    style W1 fill:#9c27b0
    style D1 fill:#4caf50
    style P1 fill:#ff9800
```

---

## Future Enhancements

1. **Authentication & Authorization**
   - JWT token-based auth
   - Role-based access control
   - Secure WebSocket connections

2. **Real FCM Integration**
   - Replace mock FCM with actual Firebase
   - Handle FCM token management
   - Push notification delivery

3. **Advanced Monitoring**
   - Prometheus metrics
   - Grafana dashboards
   - Alert management

4. **Clustering Support**
   - Redis for session sharing
   - Message broker (RabbitMQ/Kafka)
   - Distributed caching

5. **Enhanced Error Handling**
   - Circuit breaker pattern
   - Retry mechanisms
   - Dead letter queues

---

## Summary

This architecture provides:

✅ **Real-time Communication**: WebSocket with STOMP protocol  
✅ **Large Payload Support**: Compression (>100KB) and Chunking (>1MB)  
✅ **Scalability**: Thread pooling, connection pooling, rate limiting  
✅ **Resilience**: Auto-reconnect, health checks, error handling  
✅ **Performance**: Async processing, targeted topics, optimized buffers  
✅ **Maintainability**: Clean separation of concerns, service layer pattern  

The system efficiently handles device monitoring with real-time updates while managing large data payloads through intelligent compression and chunking strategies.
