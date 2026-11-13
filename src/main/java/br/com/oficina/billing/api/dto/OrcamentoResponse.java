package br.com.oficina.billing.api.dto;

import br.com.oficina.billing.domain.orcamento.Orcamento;
import br.com.oficina.billing.domain.orcamento.StatusOrcamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrcamentoResponse(
        UUID id,
        UUID osId,
        UUID clienteId,
        String nomeCliente,
        StatusOrcamento status,
        List<Item> itens,
        BigDecimal valorTotal,
        String motivo,
        LocalDateTime dataGeracao,
        LocalDateTime dataDecisao
) {
    public record Item(String tipo, String descricao, BigDecimal precoUnitario, Integer quantidade, BigDecimal precoTotal) {
    }

    public static OrcamentoResponse de(Orcamento o) {
        return new OrcamentoResponse(o.getId(), o.getOsId(), o.getClienteId(), o.getNomeCliente(), o.getStatus(),
                o.getItens().stream().map(i -> new Item(i.getTipo(), i.getDescricao(), i.getPrecoUnitario(),
                        i.getQuantidade(), i.getPrecoTotal())).toList(),
                o.getValorTotal(), o.getMotivo(), o.getDataGeracao(), o.getDataDecisao());
    }
}
