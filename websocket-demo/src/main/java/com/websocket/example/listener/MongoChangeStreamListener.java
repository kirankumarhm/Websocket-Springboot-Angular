package com.websocket.example.listener;

import com.websocket.example.model.DeviceData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.mapping.event.MongoMappingEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.Map;



@Component
public class MongoChangeStreamListener {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleDeviceDataChange(MongoMappingEvent<?> event) {
        if (event.getSource() instanceof DeviceData) {
            DeviceData deviceData = (DeviceData) event.getSource();
            
            // Send real-time update to Angular frontend
            Map<String, Object> update = new HashMap<>();
            update.put("deviceId", deviceData.getDeviceId());
            update.put("data", deviceData);
            update.put("timestamp", System.currentTimeMillis());
            
            messagingTemplate.convertAndSend("/topic/device-updates", update);
        }
    }


}
