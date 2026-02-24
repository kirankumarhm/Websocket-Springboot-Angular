package com.websocket.example.controller;

import com.websocket.example.service.FCMService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class SearchController {

    @Autowired
    private FCMService fcmService;

    @PostMapping("/search-device")
    public Map<String, String> searchDevice(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        
        // Send FCM to device
        fcmService.sendDeviceInfoRequest(phoneNumber);
        
        return Map.of("message", "FCM request sent to device: " + phoneNumber);
    }
}
