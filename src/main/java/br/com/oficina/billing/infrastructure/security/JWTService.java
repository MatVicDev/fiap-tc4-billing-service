package br.com.oficina.billing.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;

@Service
public class JWTService {

    @Value("${jwt.secret}")
    private String chaveSecreta;


    public String extrairUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String extrairRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public boolean isTokenValido(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSecretKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
                Base64.getEncoder().encodeToString(chaveSecreta.getBytes()));
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
