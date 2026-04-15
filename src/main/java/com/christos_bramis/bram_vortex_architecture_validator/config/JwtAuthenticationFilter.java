package com.christos_bramis.bram_vortex_architecture_validator.config;

import com.christos_bramis.bram_vortex_architecture_validator.service.VaultService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.security.PublicKey;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final VaultService vaultService;
    private PublicKey publicKey;

    public JwtAuthenticationFilter(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    @PostConstruct
    public void init() {
        try {
            String pem = vaultService.getSigningPublicKey();
            if (pem != null) {
                this.publicKey = vaultService.getKeyFromPEM(pem);
                System.out.println("✅ Architecture Validator: JWT Public Key loaded.");
            }
        } catch (Exception e) {
            System.err.println("❌ Critical Error loading Public Key in Architecture Validator: " + e.getMessage());
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (publicKey != null && authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                // ΕΠΑΛΗΘΕΥΣΗ: Χρήση του Public Key για RSA verification
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(publicKey)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String userId = claims.getSubject();

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, token, Collections.emptyList());

                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception e) {
                System.out.println("⛔ Invalid Token in Architecture Validator: " + e.getMessage());
                System.out.println(token);
            }
        }
        filterChain.doFilter(request, response);
    }
}