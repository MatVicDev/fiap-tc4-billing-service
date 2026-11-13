package br.com.oficina.billing.infrastructure.gateway;

import br.com.oficina.billing.domain.orcamento.Orcamento;
import br.com.oficina.billing.domain.pagamento.GatewayPagamento;
import br.com.oficina.billing.domain.pagamento.Pagamento;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulação do Mercado Pago para CI e demonstrações sem credenciais. O link de pagamento
 * aponta para uma página de checkout simulado do próprio billing-service, que permite
 * aprovar ou recusar o pagamento e dispara o mesmo fluxo do webhook real.
 */
@Slf4j
public class FakeGatewayPagamento implements GatewayPagamento {
    public static final String PREFIXO = "fake-";

    private final MercadoPagoProperties props;
    private final Map<String, ConsultaPagamento> pagamentos = new ConcurrentHashMap<>();
    private final Set<String> estornos = ConcurrentHashMap.newKeySet();

    public FakeGatewayPagamento(MercadoPagoProperties props) {
        this.props = props;
    }

    @Override
    public Cobranca criarCobranca(Pagamento pagamento, Orcamento orcamento) {
        return new Cobranca("pref-" + pagamento.getId(),
                props.urlPublica() + "/pagamentos/" + pagamento.getId() + "/checkout-simulado");
    }

    /** Registra o resultado escolhido no checkout simulado e devolve o id "do provedor". */
    public String simular(Pagamento pagamento, boolean aprovado) {
        String id = PREFIXO + pagamento.getId();
        pagamentos.put(id, new ConsultaPagamento(id, aprovado ? Situacao.APROVADO : Situacao.RECUSADO,
                aprovado ? "accredited" : "cc_rejected_other_reason", pagamento.getId().toString(), pagamento.getValor()));
        return id;
    }

    @Override
    public ConsultaPagamento consultar(String gatewayPagamentoId) {
        return pagamentos.getOrDefault(gatewayPagamentoId,
                new ConsultaPagamento(gatewayPagamentoId, Situacao.PENDENTE, null, null, null));
    }

    @Override
    public void estornar(String gatewayPagamentoId, BigDecimal valor, String chaveIdempotencia) {
        if (estornos.add(chaveIdempotencia)) {
            log.warn("[FAKE] Estorno de R$ {} do pagamento {}", valor, gatewayPagamentoId);
        }
    }
}
