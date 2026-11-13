package br.com.oficina.billing.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Valida o JWT emitido pelo os-service (login de funcionário) ou pela Lambda de CPF
 * (cliente) — os três microsserviços compartilham o mesmo segredo de assinatura, então
 * a autenticação é montada só a partir das claims, sem consultar outro serviço.
 */
@Component
@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {
    private final JWTService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String cabecalho = request.getHeader("Authorization");
        if (cabecalho != null && cabecalho.startsWith("Bearer ")) {
            String token = cabecalho.substring(7);
            if (jwtService.isTokenValido(token)) {
                String role = jwtService.extrairRole(token);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        jwtService.extrairUsername(token), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + (role == null ? "ADMIN" : role)))));
            }
        }
        filterChain.doFilter(request, response);
    }
}
