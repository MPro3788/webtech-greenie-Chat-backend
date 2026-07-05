package com.greenie.chat.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HtwAuthService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();

    @Value("${htw.api.auth-url:}")
    private String htwAuthUrl;

    public LoginResponse login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        validateCredentials(normalizedUsername, password);

        String token = UUID.randomUUID().toString();
        activeSessions.put(token, normalizedUsername);

        return new LoginResponse(token, normalizedUsername, normalizedUsername);
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            activeSessions.remove(stripBearer(token));
        }
    }

    public boolean isValidToken(String token) {
        return token != null && activeSessions.containsKey(stripBearer(token));
    }

    public String usernameForToken(String token) {
        return activeSessions.get(stripBearer(token));
    }

    private void validateCredentials(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("HTW-Benutzername und Passwort sind erforderlich.");
        }

        if (htwAuthUrl != null && !htwAuthUrl.isBlank()) {
            validateViaHtwApi(username, password);
            return;
        }

        if (!username.matches("^[sS]\\d{7}$")) {
            throw new IllegalArgumentException("Ungueltiger HTW-Benutzername (Format: s1234567).");
        }
    }

    private void validateViaHtwApi(String username, String password) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(
                    Map.of("username", username, "password", password),
                    headers
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(htwAuthUrl, request, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalArgumentException("HTW-Anmeldung fehlgeschlagen.");
            }

            Map<?, ?> body = response.getBody();
            if (body != null && body.containsKey("success") && Boolean.FALSE.equals(body.get("success"))) {
                throw new IllegalArgumentException("HTW-Zugangsdaten sind ungueltig.");
            }
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("HTW-API nicht erreichbar: " + ex.getMessage());
        }
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String stripBearer(String token) {
        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }
}
