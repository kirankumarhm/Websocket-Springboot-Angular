package com.websocket.example.repository;

import com.websocket.example.model.DeviceData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends MongoRepository<DeviceData, String> {
    DeviceData findByDeviceId(String deviceId);
}
