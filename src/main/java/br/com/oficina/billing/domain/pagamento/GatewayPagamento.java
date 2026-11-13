package br.com.oficina.billing.domain.pagamento;

import br.com.oficina.billing.domain.orcamento.Orcamento;

import java.math.BigDecimal;

/**
 * Porta para o provedor de pagamentos (Mercado Pago em produção; fake em CI/demo).
 */
public interface GatewayPagamento {

    /** Cria a cobrança (preferência de Checkout) e devolve o link de pagamento para o cliente. */
    Cobranca criarCobranca(Pagamento pagamento, Orcamento orcamento);

    /** Consulta o pagamento no provedor — a notificação (webhook) só traz o id, nunca é confiada. */
    ConsultaPagamento consultar(String gatewayPagamentoId);

    /** Estorno total do pagamento (compensação da Saga). */
    void estornar(String gatewayPagamentoId, BigDecimal valor, String chaveIdempotencia);

    record Cobranca(String gatewayCobrancaId, String linkPagamento) {
    }

    record ConsultaPagamento(String gatewayPagamentoId, Situacao situacao, String detalhe, String referenciaExterna,
                             BigDecimal valor) {
    }

    enum Situacao {
        APROVADO,
        RECUSADO,
        PENDENTE
    }
}
