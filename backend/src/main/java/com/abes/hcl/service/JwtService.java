package com.abes.hcl.service;

import com.abes.hcl.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(
            @Value("${security.jwt.secret}") String base64Secret,
            @Value("${security.jwt.expiration-ms:3600000}") long expirationMillis) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(base64Secret);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("JWT_SECRET_BASE64 must be a valid Base64-encoded secret", exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET_BASE64 must contain at least 32 random bytes");
        }
        if (expirationMillis < 60_000 || expirationMillis > 86_400_000) {
            throw new IllegalArgumentException("JWT expiration must be between 1 minute and 24 hours");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .claim("name", user.getName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        return claims.getSubject().equals(userDetails.getUsername())
                && claims.getExpiration().after(new Date());
    }

    public long getExpirationSeconds() {
        return expirationMillis / 1000;
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
