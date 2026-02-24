package com.websocket.example.service;

import com.websocket.example.model.DeviceData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LargePayloadService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Compress large payload before sending via WebSocket
     */
    public void sendLargePayload(String phoneNumber, DeviceData deviceData) {
        try {
            // Convert to JSON
            String jsonData = objectMapper.writeValueAsString(deviceData);
            
            // Check if compression is needed (>100KB)
            if (jsonData.length() > 100 * 1024) {
                // Compress the payload
                String compressedData = compressData(jsonData);
                
                Map<String, Object> message = new HashMap<>();
                message.put("compressed", true);
                message.put("data", compressedData);
                message.put("originalSize", jsonData.length());
                message.put("compressedSize", compressedData.length());
                message.put("timestamp", System.currentTimeMillis());
                
                sendToPhoneSpecificTopic(phoneNumber, message);
                System.out.println("Sent compressed payload: " + jsonData.length() + " -> " + compressedData.length() + " bytes");
            } else {
                // Send uncompressed for small payloads
                Map<String, Object> message = new HashMap<>();
                message.put("compressed", false);
                message.put("data", deviceData);
                message.put("timestamp", System.currentTimeMillis());
                
                sendToPhoneSpecificTopic(phoneNumber, message);
                System.out.println("Sent uncompressed payload: " + jsonData.length() + " bytes");
            }
        } catch (Exception e) {
            System.err.println("Error sending large payload: " + e.getMessage());
        }
    }

    /**
     * Send chunked payload for very large data (>1MB)
     */
    public void sendChunkedPayload(String phoneNumber, DeviceData deviceData) {
        try {
            String jsonData = objectMapper.writeValueAsString(deviceData);
            int chunkSize = 500 * 1024; // 500KB chunks
            
            if (jsonData.length() > chunkSize) {
                String sessionId = generateSessionId();
                int totalChunks = (int) Math.ceil((double) jsonData.length() / chunkSize);
                
                for (int i = 0; i < totalChunks; i++) {
                    int start = i * chunkSize;
                    int end = Math.min(start + chunkSize, jsonData.length());
                    String chunk = jsonData.substring(start, end);
                    
                    Map<String, Object> chunkMessage = new HashMap<>();
                    chunkMessage.put("chunked", true);
                    chunkMessage.put("sessionId", sessionId);
                    chunkMessage.put("chunkIndex", i);
                    chunkMessage.put("totalChunks", totalChunks);
                    chunkMessage.put("data", chunk);
                    chunkMessage.put("timestamp", System.currentTimeMillis());
                    
                    sendToPhoneSpecificTopic(phoneNumber, chunkMessage);
                    
                    // Small delay between chunks to prevent overwhelming
                    Thread.sleep(10);
                }
                
                System.out.println("Sent chunked payload: " + totalChunks + " chunks, " + jsonData.length() + " total bytes");
            } else {
                // Use regular method for smaller payloads
                sendLargePayload(phoneNumber, deviceData);
            }
        } catch (Exception e) {
            System.err.println("Error sending chunked payload: " + e.getMessage());
        }
    }

    private void sendToPhoneSpecificTopic(String phoneNumber, Object message) {
        String topic = "/topic/device-updates/" + phoneNumber;
        messagingTemplate.convertAndSend(topic, message);
    }

    private String compressData(String data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(data.getBytes(StandardCharsets.UTF_8));
        }
        return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    public String decompressData(String compressedData) throws IOException {
        byte[] compressed = java.util.Base64.getDecoder().decode(compressedData);
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        try (GZIPInputStream gzipIn = new GZIPInputStream(bais)) {
            return new String(gzipIn.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String generateSessionId() {
        return "session_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
}
