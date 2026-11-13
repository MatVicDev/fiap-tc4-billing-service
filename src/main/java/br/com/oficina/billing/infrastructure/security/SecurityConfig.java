package br.com.oficina.billing.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Webhook do Mercado Pago é público (autenticado pela assinatura x-signature); o cliente
     * (token da Lambda de CPF) pode consultar e decidir sobre o próprio orçamento.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JWTFilter jwtFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/webhooks/**").permitAll()
                        .requestMatchers("/pagamentos/*/checkout-simulado", "/pagamentos/*/simular").permitAll()
                        .requestMatchers(HttpMethod.GET, "/orcamentos/**").hasAnyRole("ADMIN", "CLIENTE")
                        .requestMatchers(HttpMethod.POST, "/orcamentos/*/aprovar", "/orcamentos/*/rejeitar").hasAnyRole("ADMIN", "CLIENTE")
                        .anyRequest().hasRole("ADMIN"))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
