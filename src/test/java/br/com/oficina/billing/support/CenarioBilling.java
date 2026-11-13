package br.com.oficina.billing.support;

import br.com.oficina.billing.application.DecisaoOrcamentoUseCase;
import br.com.oficina.billing.application.ProcessarComandoUseCase;
import br.com.oficina.billing.application.ProcessarNotificacaoPagamentoUseCase;
import br.com.oficina.billing.domain.mensageria.Mensagem;
import br.com.oficina.billing.domain.orcamento.Orcamento;
import br.com.oficina.billing.domain.pagamento.Pagamento;
import br.com.oficina.billing.infrastructure.gateway.FakeGatewayPagamento;
import br.com.oficina.billing.infrastructure.gateway.MercadoPagoProperties;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CenarioBilling {
    public final Fakes.Orcamentos orcamentos = new Fakes.Orcamentos();
    public final Fakes.Pagamentos pagamentos = new Fakes.Pagamentos();
    public final Fakes.Publicador publicador = new Fakes.Publicador();
    public final FakeGatewayPagamento gateway = new FakeGatewayPagamento(
            new MercadoPagoProperties("fake", null, null, null, null, "http://billing"));
    public final ProcessarComandoUseCase comandos =
            new ProcessarComandoUseCase(orcamentos, pagamentos, gateway, publicador, new Fakes.Processadas());
    public final DecisaoOrcamentoUseCase decisao = new DecisaoOrcamentoUseCase(orcamentos, pagamentos, gateway, publicador);
    public final ProcessarNotificacaoPagamentoUseCase notificacao =
            new ProcessarNotificacaoPagamentoUseCase(gateway, pagamentos, orcamentos, publicador);

    public final UUID osId = UUID.randomUUID();
    public static final String CPF = "12345678909";

    public void comando(String tipo, Map<String, Object> dados) {
        comandos.processar(Mensagem.nova(tipo, osId, "corr", dados));
    }

    public Orcamento gerarOrcamento() {
        comando("billing.gerar-orcamento", Map.of(
                "clienteId", UUID.randomUUID().toString(), "nomeCliente", "Ana", "emailCliente", "ana@email.com",
                "cpfCliente", CPF,
                "itens", List.of(
                        Map.of("tipo", "SERVICO", "referenciaId", UUID.randomUUID().toString(), "descricao", "Revisão",
                                "precoUnitario", "200.00", "quantidade", 1),
                        Map.of("tipo", "INSUMO", "referenciaId", UUID.randomUUID().toString(), "descricao", "Óleo",
                                "precoUnitario", "45.50", "quantidade", 2))));
        return orcamentos.buscarPorOsId(osId).orElseThrow();
    }

    public Pagamento aprovarEPagar(boolean aprovado) {
        Orcamento o = gerarOrcamento();
        Pagamento p = decisao.aprovar(o.getId(), CPF);
        notificacao.processar(gateway.simular(p, aprovado));
        return p;
    }
}
