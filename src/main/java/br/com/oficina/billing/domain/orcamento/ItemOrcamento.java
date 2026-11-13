package br.com.oficina.billing.domain.orcamento;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Embeddable
@NoArgsConstructor
public class ItemOrcamento {

    @Column(nullable = false, length = 10)
    private String tipo;

    private UUID referenciaId;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false)
    private BigDecimal precoUnitario;

    @Column(nullable = false)
    private Integer quantidade;

    public ItemOrcamento(String tipo, UUID referenciaId, String descricao, BigDecimal precoUnitario, Integer quantidade) {
        if (quantidade == null || quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
        if (precoUnitario == null || precoUnitario.signum() < 0) {
            throw new IllegalArgumentException("Preço unitário inválido.");
        }
        this.tipo = tipo;
        this.referenciaId = referenciaId;
        this.descricao = descricao;
        this.precoUnitario = precoUnitario;
        this.quantidade = quantidade;
    }

    public BigDecimal getPrecoTotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    public String getTipo() { return tipo; }
    public UUID getReferenciaId() { return referenciaId; }
    public String getDescricao() { return descricao; }
    public BigDecimal getPrecoUnitario() { return precoUnitario; }
    public Integer getQuantidade() { return quantidade; }
}
