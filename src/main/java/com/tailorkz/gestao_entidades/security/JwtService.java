package com.tailorkz.gestao_entidades.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey chaveAssinatura;
    private final long expiracaoHoras;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiracao-horas:8}") long expiracaoHoras) {
        this.chaveAssinatura = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiracaoHoras = expiracaoHoras;
    }

    public String gerarToken(UUID usuarioId, String role, UUID tenantId, String nome, String categoria) {
        Date agora = new Date();
        Date expiracao = new Date(agora.getTime() + expiracaoHoras * 3600_000L);

        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim("role", role)
                .claim("tenantId", tenantId != null ? tenantId.toString() : null)
                .claim("nome", nome)
                .claim("categoria", categoria != null ? categoria : "")
                .issuedAt(agora)
                .expiration(expiracao)
                .signWith(chaveAssinatura)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(chaveAssinatura)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}