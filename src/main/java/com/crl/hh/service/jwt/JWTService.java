package com.crl.hh.service.jwt;

import com.crl.hh.repository.UserRepository;
import com.crl.hh.repository.models.User;
import com.crl.hh.repository.models.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JWTService {

    private final UserRepository userRepository;

    @Value("${security.jwt.secret-key}")
    private String secretKey;

    @Value("${security.jwt.expiration-time}")
    private long expirationTime;

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String generateToken(String username) {
        Optional<User> optionalUser = userRepository.findUserByUsername(username);

        if (optionalUser.isPresent()) {
            return Jwts
                    .builder()
                    .subject(username)
                    .claim("role", optionalUser.get().getRole())
                    .issuedAt(new Date())
                    .expiration(new Date((new Date().getTime() + expirationTime)))
                    .signWith(getSecretKey())
                    .compact();
        }

        return "User with name: " + username + " not found";
    }

    private Claims getAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return getAllClaims(token).getSubject();
    }

    public Role extractRole(String token) {
        return getAllClaims(token).get("role", Role.class);
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);

        final Date expiration = getAllClaims(token).getExpiration();
        boolean notExpired = expiration.after(new Date());

        return (username.equals(userDetails.getUsername()) && notExpired);
    }
}
