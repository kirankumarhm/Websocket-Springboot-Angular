package com.websocket.example.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "devices")
public class DeviceData {
    @Id
    private String id;
    private String deviceId;
    private String phoneNumber;
    private String wifiStatus;
    private Integer batteryLevel;
    private String storageUsed;
    private String signalStrength;
    private String model;
    private String firmware;
    private String imei;
    
    // Getters and setters
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    
    public String getWifiStatus() { return wifiStatus; }
    public void setWifiStatus(String wifiStatus) { this.wifiStatus = wifiStatus; }
    
    public Integer getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Integer batteryLevel) { this.batteryLevel = batteryLevel; }
    
    public String getStorageUsed() { return storageUsed; }
    public void setStorageUsed(String storageUsed) { this.storageUsed = storageUsed; }
    
    public String getSignalStrength() { return signalStrength; }
    public void setSignalStrength(String signalStrength) { this.signalStrength = signalStrength; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public String getFirmware() { return firmware; }
    public void setFirmware(String firmware) { this.firmware = firmware; }
    
    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }


    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}
