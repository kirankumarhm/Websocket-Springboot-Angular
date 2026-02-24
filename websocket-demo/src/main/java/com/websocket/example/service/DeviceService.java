package com.websocket.example.service;
import com.websocket.example.model.DeviceData;
import com.websocket.example.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeviceService {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private LargePayloadService largePayloadService;

    public List<DeviceData> findAll() {
        return deviceRepository.findAll();
    }

    public DeviceData findByDeviceId(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId);
    }

    public DeviceData save(DeviceData deviceData) {
        DeviceData saved = deviceRepository.save(deviceData);
        notifyFrontend(saved);
        return saved;
    }

    public DeviceData upsert(DeviceData deviceData) {
        System.out.println("Upsert called - DeviceId: " + deviceData.getDeviceId() + ", Phone: " + deviceData.getPhoneNumber());
        DeviceData existing = findByDeviceId(deviceData.getDeviceId());

        if (existing != null) {
            // Update existing device with non-null values
            if (deviceData.getPhoneNumber() != null) existing.setPhoneNumber(deviceData.getPhoneNumber());
            if (deviceData.getWifiStatus() != null) existing.setWifiStatus(deviceData.getWifiStatus());
            if (deviceData.getBatteryLevel() != null) existing.setBatteryLevel(deviceData.getBatteryLevel());
            if (deviceData.getStorageUsed() != null) existing.setStorageUsed(deviceData.getStorageUsed());
            if (deviceData.getSignalStrength() != null) existing.setSignalStrength(deviceData.getSignalStrength());
            if (deviceData.getModel() != null) existing.setModel(deviceData.getModel());
            if (deviceData.getFirmware() != null) existing.setFirmware(deviceData.getFirmware());
            if (deviceData.getImei() != null) existing.setImei(deviceData.getImei());
            return save(existing); // This calls notifyFrontend automatically
        } else {
            // Create new device
            return save(deviceData); // This calls notifyFrontend automatically
        }
    }

    public void deleteByDeviceId(String deviceId) {
        DeviceData device = findByDeviceId(deviceId);
        if (device != null) {
            deviceRepository.delete(device);
        }
    }

    private void notifyFrontend(DeviceData deviceData) {
        System.out.println("NotifyFrontend called - DeviceId: " + deviceData.getDeviceId() + ", Phone: " + deviceData.getPhoneNumber());
        
        if (deviceData.getPhoneNumber() != null) {
            // Use LargePayloadService for handling potentially large device data
            largePayloadService.sendLargePayload(deviceData.getPhoneNumber(), deviceData);
            System.out.println("Sent large payload update for phone: " + deviceData.getPhoneNumber());
        } else {
            // Fallback to regular messaging for data without phone number
            Map<String, Object> update = new HashMap<>();
            update.put("deviceId", deviceData.getDeviceId());
            update.put("data", deviceData);
            update.put("timestamp", System.currentTimeMillis());
            
            System.out.println("No phone number found, sending to general topic");
            messagingTemplate.convertAndSend("/topic/device-updates", update);
        }
    }
}
