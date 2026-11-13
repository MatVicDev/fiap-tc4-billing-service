package br.com.oficina.billing.domain.pagamento;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cobrança de um orçamento aprovado. O {@code id} é enviado ao Mercado Pago como
 * {@code external_reference}, e é por ele que a notificação de pagamento é correlacionada.
 */
@Entity
@NoArgsConstructor
@Table(name = "pagamentos", indexes = {
        @Index(name = "idx_pagamento_orcamento", columnList = "orcamentoId"),
        @Index(name = "idx_pagamento_gateway", columnList = "gatewayPagamentoId")
})
public class Pagamento {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID orcamentoId;

    @Column(nullable = false)
    private UUID osId;

    @Column(nullable = false)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPagamento status;

    /** Id da preferência (Checkout Pro) no Mercado Pago. */
    private String gatewayCobrancaId;

    /** Id do pagamento efetivado no Mercado Pago (chega pela notificação). */
    private String gatewayPagamentoId;

    @Column(length = 1000)
    private String linkPagamento;

    private String detalheStatus;

    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    @Version
    private Long versao;

    public Pagamento(UUID orcamentoId, UUID osId, BigDecimal valor) {
        this.id = UUID.randomUUID();
        this.orcamentoId = orcamentoId;
        this.osId = osId;
        this.valor = valor;
        this.status = StatusPagamento.PENDENTE;
        this.criadoEm = LocalDateTime.now();
    }

    public void registrarCobranca(String gatewayCobrancaId, String linkPagamento) {
        this.gatewayCobrancaId = gatewayCobrancaId;
        this.linkPagamento = linkPagamento;
        tocar();
    }

    public void aprovar(String gatewayPagamentoId, String detalhe) {
        exigirPendente();
        this.gatewayPagamentoId = gatewayPagamentoId;
        this.detalheStatus = detalhe;
        this.status = StatusPagamento.APROVADO;
        tocar();
    }

    public void recusar(String gatewayPagamentoId, String detalhe) {
        exigirPendente();
        this.gatewayPagamentoId = gatewayPagamentoId;
        this.detalheStatus = detalhe;
        this.status = StatusPagamento.RECUSADO;
        tocar();
    }

    public void estornar() {
        if (status != StatusPagamento.APROVADO) {
            throw new IllegalStateException("Só é possível estornar pagamento aprovado (atual: " + status + ").");
        }
        this.status = StatusPagamento.ESTORNADO;
        tocar();
    }

    private void exigirPendente() {
        if (status != StatusPagamento.PENDENTE) {
            throw new IllegalStateException("Pagamento já está " + status + ".");
        }
    }

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getOrcamentoId() { return orcamentoId; }
    public UUID getOsId() { return osId; }
    public BigDecimal getValor() { return valor; }
    public StatusPagamento getStatus() { return status; }
    public String getGatewayCobrancaId() { return gatewayCobrancaId; }
    public String getGatewayPagamentoId() { return gatewayPagamentoId; }
    public String getLinkPagamento() { return linkPagamento; }
    public String getDetalheStatus() { return detalheStatus; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
