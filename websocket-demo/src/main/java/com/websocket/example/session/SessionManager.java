package com.websocket.example.session;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Track active sessions
@Component
public class SessionManager {
    private final Map<String, Set<String>> phoneToSessions = new ConcurrentHashMap<>();
    
    public void addSession(String phoneNumber, String sessionId) {
        phoneToSessions.computeIfAbsent(phoneNumber, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }
}
