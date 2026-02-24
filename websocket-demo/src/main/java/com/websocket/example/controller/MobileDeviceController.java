package com.websocket.example.controller;

import com.websocket.example.model.DeviceData;
import com.websocket.example.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/mobile")
public class MobileDeviceController {

    @Autowired
    private DeviceService deviceService;

    // Endpoint for mobile devices to send telemetry data (upsert)
    @PostMapping("/telemetry")
    public String receiveTelemetry(@RequestBody DeviceData deviceData) {
        System.out.println("Received telemetry for device: " + deviceData.getDeviceId() + ", phone: " + deviceData.getPhoneNumber());
        deviceService.upsert(deviceData);
        return "Telemetry received for device: " + deviceData.getDeviceId();
    }

    // Endpoint for FCM response handling (upsert)
    @PostMapping("/fcm-response/{deviceId}")
    public String handleFcmResponse(@PathVariable String deviceId, @RequestBody Map<String, Object> response) {
        DeviceData device = deviceService.findByDeviceId(deviceId);
        
        if (device == null) {
            // Create new device if not exists
            device = new DeviceData();
            device.setDeviceId(deviceId);
        }
        
        // Update device with FCM response data
        if (response.get("wifiStatus") != null) {
            device.setWifiStatus((String) response.get("wifiStatus"));
        }
        if (response.get("batteryLevel") != null) {
            device.setBatteryLevel((Integer) response.get("batteryLevel"));
        }
        
        deviceService.save(device);
        return "FCM response processed";
    }
}
