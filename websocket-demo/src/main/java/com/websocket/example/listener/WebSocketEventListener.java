package com.websocket.example.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;



/**
 * This component:
 *
 * Listens for WebSocket disconnect events
 *
 * Logs session disconnections
 *
 * Triggers garbage collection for memory cleanup
 *
 * Ready for SessionManager integration when needed
 */

@Component
public class WebSocketEventListener {

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        System.out.println("WebSocket session disconnected: " + sessionId);
        
        // Clean up session-specific resources
        // sessionManager.removeSession(sessionId); // Uncomment when SessionManager is implemented
        
        // Suggest garbage collection for memory cleanup
        System.gc();
    }
}
