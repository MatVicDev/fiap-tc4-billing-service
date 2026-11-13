package br.com.oficina.billing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MensagemProcessadaJpa extends JpaRepository<MensagemProcessada, UUID> {
}
