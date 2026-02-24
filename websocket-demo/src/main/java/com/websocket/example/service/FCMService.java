package com.websocket.example.service;

import com.websocket.example.model.DeviceData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;
import java.util.Random;
import java.util.concurrent.Semaphore;

@Service
public class FCMService {

    @Autowired
    private DeviceService deviceService;

    // Add rate limiting for 1000 concurrent FCM requests
    private final Semaphore fcmSemaphore = new Semaphore(50); // Max 50 concurrent FCM calls

    public void sendDeviceInfoRequest(String phoneNumber) {
        System.out.println("Mock FCM: Sending request to device with phone: " + phoneNumber);
        
        // Simulate FCM delay and mock device response
        CompletableFuture.runAsync(() -> {
            try {
                fcmSemaphore.acquire(); // Rate limit
                Thread.sleep(2000); // Simulate 2 second delay
                simulateDeviceResponse(phoneNumber);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                fcmSemaphore.release();
            }
        });
    }

    private void simulateDeviceResponse(String phoneNumber) {
        System.out.println("Mock Device Response: Sending telemetry for phone: " + phoneNumber);
        
        Random random = new Random();
        
        DeviceData mockDevice = new DeviceData();
        mockDevice.setDeviceId("DEVICE_" + phoneNumber.substring(phoneNumber.length() - 4));
        mockDevice.setPhoneNumber(phoneNumber);
        mockDevice.setWifiStatus(random.nextBoolean() ? "Connected" : "Disconnected");
        mockDevice.setBatteryLevel(20 + random.nextInt(80)); // 20-100%
        mockDevice.setStorageUsed(String.valueOf(10 + random.nextInt(50))); // 10-60GB
        mockDevice.setSignalStrength(getRandomSignal());
        mockDevice.setModel(getRandomModel());
        mockDevice.setFirmware(getRandomFirmware());
        mockDevice.setImei("IMEI_" + System.currentTimeMillis());
        
        // Save device data (triggers WebSocket notification)
        deviceService.upsert(mockDevice);
        
        System.out.println("Mock device response processed for: " + phoneNumber);
    }

    private String getRandomSignal() {
        String[] signals = {"Excellent", "Good", "Fair", "Poor"};
        return signals[new Random().nextInt(signals.length)];
    }

    private String getRandomModel() {
        String[] models = {"iPhone 14", "Samsung Galaxy S23", "Google Pixel 7", "OnePlus 11"};
        return models[new Random().nextInt(models.length)];
    }

    private String getRandomFirmware() {
        String[] firmwares = {"iOS 17.1", "Android 14", "Android 13", "iOS 16.5"};
        return firmwares[new Random().nextInt(firmwares.length)];
    }
}
