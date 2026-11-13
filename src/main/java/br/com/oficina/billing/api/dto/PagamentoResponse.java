package br.com.oficina.billing.api.dto;

import br.com.oficina.billing.domain.pagamento.Pagamento;
import br.com.oficina.billing.domain.pagamento.StatusPagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PagamentoResponse(
        UUID id,
        UUID orcamentoId,
        UUID osId,
        BigDecimal valor,
        StatusPagamento status,
        String linkPagamento,
        String gatewayPagamentoId,
        String detalheStatus,
        LocalDateTime criadoEm
) {
    public static PagamentoResponse de(Pagamento p) {
        return new PagamentoResponse(p.getId(), p.getOrcamentoId(), p.getOsId(), p.getValor(), p.getStatus(),
                p.getLinkPagamento(), p.getGatewayPagamentoId(), p.getDetalheStatus(), p.getCriadoEm());
    }
}
