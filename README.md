# Websocket-Springboot-Angular

# WebSocket Device Dashboard

Real-time device monitoring system using Spring Boot WebSocket backend and Angular frontend with support for large payloads and FCM integration.

## Architecture

```
Angular Frontend ←→ WebSocket (STOMP/SockJS) ←→ Spring Boot Backend ←→ MongoDB
                                ↓
                           FCM Service (Mock)
```

## Backend Implementation (Spring Boot 3.5.7)

### Dependencies
- `spring-boot-starter-websocket` - WebSocket support
- `spring-boot-starter-data-mongodb` - MongoDB integration
- `spring-boot-starter-web` - REST endpoints

### Configuration

**WebSocket Setup (`WebSocketConfiguration.java`):**
```java
@EnableWebSocketMessageBroker
- Endpoint: /socket with SockJS fallback
- Message broker: /topic prefix
- Application prefix: /app
- Large payload: 1MB message size, 2MB buffer
- Heartbeat: 10s/20s intervals
```

**Database (`application.yaml`):**
```yaml
mongodb:
  uri: mongodb://localhost:27017/devicedb
websocket:
  max-message-size: 1048576 # 1MB
  send-buffer-size: 2097152 # 2MB
```

### Core Components

**DeviceData Model:**
```java
@Document(collection = "devices")
- deviceId, phoneNumber, wifiStatus
- batteryLevel, storageUsed, signalStrength
- model, firmware, imei
```

**DeviceService:**
- CRUD operations with upsert functionality
- Real-time WebSocket notifications
- Phone-specific topic routing: `/topic/device-updates/{phoneNumber}`

**LargePayloadService:**
- Compression: GZIP + Base64 for payloads >100KB
- Chunking: 500KB chunks for payloads >1MB
- Session-based reconstruction

**FCMService (Mock):**
- Simulates FCM with 2-second delay
- Rate limiting: 50 concurrent requests
- Generates random device telemetry

### REST Endpoints

```
POST /api/search-device - Trigger FCM request
POST /mobile/telemetry - Receive device data
POST /mobile/fcm-response/{deviceId} - Handle FCM responses
```

### WebSocket Topics

```
/topic/device-updates/{phoneNumber} - Phone-specific updates
/topic/device-updates - General updates
```

## Frontend Implementation (Angular 20)

### Dependencies
- `@stomp/stompjs` - STOMP protocol
- `sockjs-client` - SockJS transport
- Angular standalone components

### WebSocket Service
```typescript
connect() {
  const socket = new SockJS('http://localhost:8080/socket');
  return Stomp.over(socket);
}
```

### Main Features

**Phone Search:**
- Input phone number
- HTTP POST to trigger FCM
- Subscribe to phone-specific WebSocket topic

**Real-time Updates:**
- Handles 3 payload types:
  - Regular: Direct JSON
  - Compressed: GZIP + Base64
  - Chunked: Session-based reconstruction

**Large Payload Handling:**
```typescript
handleLargePayload(update) {
  if (update.chunked) handleChunkedPayload();
  else if (update.compressed) handleCompressedPayload();
  else handleRegularPayload();
}
```

**UI Components:**
- Device telemetry display
- Payload information (size, compression, chunks)
- Real-time status updates

## Data Flow

### Search Process
1. User enters phone number
2. HTTP POST `/api/search-device`
3. FCMService sends mock FCM
4. Mock device responds with telemetry
5. DeviceService saves to MongoDB
6. LargePayloadService sends via WebSocket
7. Angular receives and displays data

### Real-time Updates
```
Device Data → MongoDB → DeviceService → LargePayloadService → WebSocket → Angular UI
```

## Large Payload Optimization

### Backend Processing
- **<100KB:** Send directly
- **>100KB:** GZIP compress + Base64 encode
- **>1MB:** Split into 500KB chunks with session ID

### Frontend Processing
- **Regular:** Direct JSON parsing
- **Compressed:** Base64 decode + GZIP decompress
- **Chunked:** Buffer chunks by session, reconstruct when complete

## Performance Features

**Scalability:**
- Phone-specific WebSocket topics
- MongoDB connection pooling (10-50 connections)
- FCM rate limiting (50 concurrent)
- Async processing

**Memory Management:**
- Automatic garbage collection on disconnect
- Session cleanup for chunked payloads
- Configurable timeouts (60 seconds)

**Error Handling:**
- WebSocket reconnection
- SockJS fallback transport
- Chunk reconstruction with session management

## Setup Instructions

### Backend
```bash
cd websocket-demo
mvn spring-boot:run
# Runs on http://localhost:8080
```

### Frontend
```bash
cd websocket-front-end
npm install
ng serve
# Runs on http://localhost:4200
```

### MongoDB
```bash
mongod --dbpath /path/to/data
# Database: devicedb, Collection: devices
```

## API Usage

### Search Device
```bash
curl -X POST http://localhost:8080/api/search-device \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "1234567890"}'
```

### Send Telemetry
```bash
curl -X POST http://localhost:8080/mobile/telemetry \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "DEVICE_001",
    "phoneNumber": "1234567890",
    "wifiStatus": "Connected",
    "batteryLevel": 85
  }'
```

## WebSocket Message Format

### Regular Message
```json
{
  "compressed": false,
  "data": { "deviceId": "...", "phoneNumber": "..." },
  "timestamp": 1234567890
}
```

### Compressed Message
```json
{
  "compressed": true,
  "data": "base64-gzip-encoded-data",
  "originalSize": 150000,
  "compressedSize": 45000,
  "timestamp": 1234567890
}
```

### Chunked Message
```json
{
  "chunked": true,
  "sessionId": "session_123",
  "chunkIndex": 0,
  "totalChunks": 5,
  "data": "chunk-data",
  "timestamp": 1234567890
}
```

## Key Files

### Backend
- `WebSocketConfiguration.java` - WebSocket setup
- `DeviceService.java` - Core business logic
- `LargePayloadService.java` - Payload optimization
- `FCMService.java` - Mock FCM implementation
- `MobileDeviceController.java` - REST endpoints

### Frontend
- `app.ts` - Main component with WebSocket handling
- `websocket.service.ts` - WebSocket connection service
- `app.html` - UI template with device dashboard

## Monitoring

**Payload Information:**
- Size tracking (KB display)
- Compression status
- Chunk count for large payloads

**Connection Status:**
- WebSocket connection state
- Subscription management
- Error handling and retry logic
