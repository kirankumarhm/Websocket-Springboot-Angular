import { Component, signal, OnInit, ChangeDetectorRef } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { WebSocketService } from './service/websocket.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  protected readonly title = signal('websocket-front-end');
  public deviceData: any = {};
  public lastUpdate = '';
  public phoneNumber = '';
  public isSearching = false;
  public searchStatus = '';
  private currentSubscription: any = null;
  private stompClient: any = null;
  private chunkBuffer: Map<string, any[]> = new Map();
  public payloadInfo = { compressed: false, size: 0, chunks: 0 };
  
  // Session tracking
  public sessionId = this.generateUUID();
  private agentId = 'agent123'; // Get from auth service
  private currentActionId: string | null = null;
  private pollingInterval: any = null;
  
  // Static mock telemetry data (generated once per session)
  public mockTelemetry: any = {};

  constructor(
    private webSocketService: WebSocketService,
    private cdr: ChangeDetectorRef,
    private http: HttpClient
  ) {}

  ngOnInit() {
    // Initialize session
    this.initializeSession();
    
    // Initial WebSocket connection
    this.initializeWebSocket();
    
    // Set up periodic connection health check
    setInterval(() => {
      this.checkWebSocketHealth();
    }, 30000); // Check every 30 seconds
  }
  
  private initializeWebSocket() {
    setTimeout(() => {
      console.log('🔌 Initializing WebSocket connection...');
      this.stompClient = this.webSocketService.connect();
      this.stompClient.connect(
        {},
        () => {
          console.log('✅ WebSocket connected successfully');
        },
        (error: any) => {
          console.error('❌ WebSocket connection error:', error);
        }
      );
    }, 1000);
  }
  
  private checkWebSocketHealth() {
    if (!this.stompClient || !this.stompClient.connected) {
      console.log('⚠️ WebSocket health check failed - connection lost');
      // Don't auto-reconnect during health check, only reconnect when user searches
    }
  }

  searchDevice() {
    if (!this.phoneNumber.trim()) {
      this.searchStatus = 'Please enter a phone number';
      return;
    }

    this.isSearching = true;
    this.searchStatus = 'Sending FCM request to device...';
    this.currentActionId = this.generateUUID();

    // Log search action start
    this.logSearchAction('START');

    // Unsubscribe from previous phone number if exists
    if (this.currentSubscription) {
      this.currentSubscription.unsubscribe();
      this.currentSubscription = null;
    }

    // Ensure WebSocket is connected before proceeding
    this.ensureWebSocketConnection(() => {
      this.subscribeToPhoneNumber(this.phoneNumber);
    });

    this.http
      .post(`http://localhost:8080/api/push/send`, {
        agentId: this.agentId,
        msisdn: this.phoneNumber,
        userAgent: navigator.userAgent
      })
      .subscribe({
        next: (response: any) => {
          this.sessionId = response.sessionId; // Use backend sessionId
          this.searchStatus = 'FCM sent successfully. Waiting for device response...';
          this.isSearching = false;
          this.logSearchAction('FCM_SENT');
          
          // Start polling for session status
          this.startPolling();
        },
        error: (error) => {
          this.searchStatus = 'Error: ' + (error.error?.message || 'Failed to send FCM');
          this.isSearching = false;
          this.logSearchAction('ERROR', error.error?.message);
        },
      });
  }

  private ensureWebSocketConnection(callback: () => void) {
    if (!this.stompClient) {
      console.log('🔄 STOMP client not initialized, reconnecting...');
      this.reconnectWebSocket(callback);
      return;
    }

    if (!this.stompClient.connected) {
      console.log('🔄 WebSocket disconnected, reconnecting...');
      this.reconnectWebSocket(callback);
      return;
    }

    // Connection is ready
    console.log('✅ WebSocket already connected');
    callback();
  }

  private reconnectWebSocket(callback: () => void) {
    console.log('🔌 Reconnecting WebSocket...');
    
    // Disconnect existing connection if any
    if (this.stompClient && this.stompClient.connected) {
      this.stompClient.disconnect();
    }
    
    // Create new connection
    this.stompClient = this.webSocketService.connect();
    this.stompClient.connect(
      {},
      () => {
        console.log('✅ WebSocket reconnected successfully');
        callback();
      },
      (error: any) => {
        console.error('❌ WebSocket reconnection failed:', error);
        // Retry after 2 seconds
        setTimeout(() => {
          console.log('🔄 Retrying WebSocket connection...');
          this.reconnectWebSocket(callback);
        }, 2000);
      }
    );
  }

  private subscribeToPhoneNumber(phoneNumber: string) {
    if (!this.stompClient || !this.stompClient.connected) {
      console.error('❌ Cannot subscribe: WebSocket not connected');
      return;
    }

    // Subscribe to general device updates topic
    const topic = `/topic/device-updates`;
    console.log('Subscribing to topic:', topic);

    this.currentSubscription = this.stompClient.subscribe(topic, (message: any) => {
      console.log('🔔 WebSocket message received!');
      console.log('Raw message:', message.body);
      
      try {
        const update = JSON.parse(message.body);
        console.log('📦 Parsed update:', update);
        console.log('📱 Update phone number:', update.phoneNumber);
        console.log('📱 Data phone number:', update.data?.phoneNumber);
        console.log('🔍 Looking for phone number:', phoneNumber);

        // Check if this update is for our phone number
        if (update.phoneNumber === phoneNumber || update.data?.phoneNumber === phoneNumber) {
          console.log('✅ Phone number matches! Processing update...');
          this.handleLargePayload(update, phoneNumber);
        } else {
          console.log('❌ Phone number does not match, ignoring update');
        }
      } catch (error) {
        console.error('❌ Error parsing WebSocket message:', error);
      }
    });

    console.log('Successfully subscribed to updates for phone:', phoneNumber);
  }

  private handleLargePayload(update: any, phoneNumber: string) {
    if (update.chunked) {
      // Handle chunked payload
      this.handleChunkedPayload(update, phoneNumber);
    } else if (update.compressed) {
      // Handle compressed payload
      this.handleCompressedPayload(update, phoneNumber);
    } else {
      // Handle regular payload
      this.handleRegularPayload(update, phoneNumber);
    }
  }

  private handleChunkedPayload(update: any, phoneNumber: string) {
    const sessionId = update.sessionId;
    const chunkIndex = update.chunkIndex;
    const totalChunks = update.totalChunks;

    // Initialize chunk buffer for this session
    if (!this.chunkBuffer.has(sessionId)) {
      this.chunkBuffer.set(sessionId, new Array(totalChunks));
    }

    // Store chunk
    const chunks = this.chunkBuffer.get(sessionId)!;
    chunks[chunkIndex] = update.data;

    // Check if all chunks received
    const receivedChunks = chunks.filter((chunk) => chunk !== undefined).length;
    this.payloadInfo.chunks = receivedChunks;

    if (receivedChunks === totalChunks) {
      // Reconstruct complete payload
      const completeData = chunks.join('');
      try {
        const deviceData = JSON.parse(completeData);
        this.updateDeviceData(deviceData, phoneNumber);
        this.payloadInfo = { compressed: false, size: completeData.length, chunks: totalChunks };

        // Clean up
        this.chunkBuffer.delete(sessionId);
        console.log(
          'Chunked payload reconstructed:',
          totalChunks,
          'chunks,',
          completeData.length,
          'bytes'
        );
      } catch (error) {
        console.error('Error parsing chunked payload:', error);
      }
    } else {
      this.searchStatus = `Receiving chunks: ${receivedChunks}/${totalChunks}`;
    }
  }

  private handleCompressedPayload(update: any, phoneNumber: string) {
    try {
      // Decompress data (Base64 decode + GZIP decompress)
      const compressedData = update.data;
      const decompressedData = this.decompressData(compressedData);
      const deviceData = JSON.parse(decompressedData);

      this.updateDeviceData(deviceData, phoneNumber);
      this.payloadInfo = {
        compressed: true,
        size: update.originalSize || decompressedData.length,
        chunks: 0,
      };

      console.log(
        'Compressed payload decompressed:',
        update.compressedSize,
        '->',
        update.originalSize,
        'bytes'
      );
    } catch (error) {
      console.error('Error decompressing payload:', error);
    }
  }

  private handleRegularPayload(update: any, phoneNumber: string) {
    console.log('🔄 Processing regular payload for phone:', phoneNumber);
    console.log('📦 Update data:', update.data);
    
    if (update.data && (update.data.phoneNumber === phoneNumber || update.phoneNumber === phoneNumber)) {
      console.log('✅ Phone number matches in regular payload, updating device data...');
      this.updateDeviceData(update.data, phoneNumber);
      const dataSize = JSON.stringify(update.data).length;
      this.payloadInfo = { compressed: false, size: dataSize, chunks: 0 };
    } else {
      console.log('❌ Phone number does not match in regular payload');
    }
  }

  private updateDeviceData(deviceData: any, phoneNumber: string) {
    console.log('🎯 UPDATING DEVICE DATA!');
    console.log('📱 Phone:', phoneNumber);
    console.log('📦 Device data:', deviceData);
    
    this.deviceData = deviceData;
    this.lastUpdate = new Date().toLocaleTimeString();
    this.searchStatus = 'Device data received via WebSocket!';
    this.isSearching = false;
    
    // Stop polling since we got WebSocket data
    this.stopPolling();
    
    // Log successful device response
    this.logSearchAction('SUCCESS');
    
    console.log('✅ Device data updated successfully!');
    console.log('📊 Final deviceData:', this.deviceData);
    this.cdr.detectChanges();
  }

  private decompressData(compressedData: string): string {
    // Simple Base64 decode (in real implementation, you'd need proper GZIP decompression)
    try {
      return atob(compressedData);
    } catch (error) {
      console.error('Decompression error:', error);
      return compressedData; // Fallback to original data
    }
  }

  private generateUUID(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
      const r = Math.random() * 16 | 0;
      const v = c == 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
  }

  private initializeSession() {
    // Session will be created when push notification is sent
    console.log('Session will be initialized on first push request');
  }

  private logSearchAction(status: string, errorMessage?: string) {
    console.log('Action logged:', status);
  }

  private startPolling() {
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval);
    }
    
    this.pollingInterval = setInterval(() => {
      this.checkSessionStatus();
    }, 2000); // Poll every 2 seconds
  }

  private checkSessionStatus() {
    if (!this.sessionId) return;
    
    this.http.get(`http://localhost:8080/api/push/status/${this.sessionId}`).subscribe({
      next: (response: any) => {
        console.log('Session status:', response);
        
        if (response.status === 'responded' && response.hasDeviceResponse) {
          this.searchStatus = 'Device responded! Fetching device data...';
          this.fetchDeviceData();
          this.stopPolling();
        } else if (response.status === 'failed') {
          this.searchStatus = 'Request failed: ' + (response.errorMessage || 'Unknown error');
          this.stopPolling();
        } else {
          this.searchStatus = `Status: ${response.status} - ${response.fcmStatus || 'Processing'}`;
        }
      },
      error: (error) => {
        console.error('Status check error:', error);
        this.stopPolling();
      }
    });
  }

  private fetchDeviceData() {
    console.log('=== FETCHING DEVICE DATA ===');
    console.log('Looking for phone number:', this.phoneNumber);
    
    // Fetch from new Device collection instead of DeviceResponse
    this.http.get<any[]>(`http://localhost:8080/api/debug/devices`).subscribe({
      next: (devices) => {
        console.log('All devices:', devices);
        console.log('Total devices found:', devices.length);
        
        // Find device for our phone number
        const device = devices.find(d => d.phoneNumber === this.phoneNumber);
        if (device) {
          console.log('✅ Found device:', device);
          
          // Create combined data structure for UI
          this.deviceData = {
            deviceId: device.deviceId,
            phoneNumber: device.phoneNumber,
            sessionId: this.sessionId,
            responseType: 'TELEMETRY',
            responseTime: device.lastUpdated,
            dataSize: 1024,
            compressed: false,
            chunks: 0,
            deviceDetails: device.deviceDetails,
            deviceTelemetry: device.latestTelemetry
          };
          
          this.lastUpdate = new Date().toLocaleTimeString();
          this.searchStatus = 'Device data received successfully!';
          
          this.cdr.detectChanges();
          console.log('✅ Device data updated in UI');
        } else {
          console.log('❌ No device found for phone:', this.phoneNumber);
          console.log('Available phone numbers:', devices.map(d => d.phoneNumber));
        }
      },
      error: (error) => {
        console.error('❌ Error fetching device data:', error);
      }
    });
  }

  private stopPolling() {
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval);
      this.pollingInterval = null;
    }
  }

  endSession() {
    if (!this.sessionId) return;
    
    this.http.post(`http://localhost:8080/api/session/end?sessionId=${this.sessionId}`, {}).subscribe({
      next: () => {
        this.searchStatus = 'Session ended. Total time tracked for reporting.';
        this.sessionId = '';
        this.deviceData = {};
      },
      error: (error) => {
        console.error('Error ending session:', error);
      }
    });
  }

  // Generate static mock telemetry data once per session
  private generateMockTelemetry() {
    this.mockTelemetry = {
      battery: 20 + Math.floor(Math.random() * 80),
      wifiStatus: ['Connected', 'Disconnected', 'Connecting'][Math.floor(Math.random() * 3)],
      signal: ['Excellent (4/4)', 'Good (3/4)', 'Fair (2/4)', 'Poor (1/4)'][Math.floor(Math.random() * 4)],
      storage: 10 + Math.floor(Math.random() * 50),
      model: ['iPhone 14 Pro', 'Samsung Galaxy S23', 'Google Pixel 7', 'OnePlus 11'][Math.floor(Math.random() * 4)],
      os: ['iOS 17.1', 'Android 14', 'Android 13', 'iOS 16.5'][Math.floor(Math.random() * 4)]
    };
    console.log('Generated static mock telemetry:', this.mockTelemetry);
  }

  // Static telemetry data access methods
  getMockBattery(): number {
    return this.mockTelemetry.battery || 0;
  }

  getMockWifiStatus(): string {
    return this.mockTelemetry.wifiStatus || 'Unknown';
  }

  getMockSignal(): string {
    return this.mockTelemetry.signal || 'Unknown';
  }

  getMockStorage(): number {
    return this.mockTelemetry.storage || 0;
  }

  getMockModel(): string {
    return this.mockTelemetry.model || 'Unknown';
  }

  getMockOS(): string {
    return this.mockTelemetry.os || 'Unknown';
  }
}
