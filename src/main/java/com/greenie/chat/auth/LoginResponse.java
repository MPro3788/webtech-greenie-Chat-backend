package com.greenie.chat.auth;

public record LoginResponse(String token, String username, String displayName) {}
