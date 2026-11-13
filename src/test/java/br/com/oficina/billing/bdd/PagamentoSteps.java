package br.com.oficina.billing.bdd;

import br.com.oficina.billing.domain.orcamento.Orcamento;
import br.com.oficina.billing.domain.pagamento.Pagamento;
import br.com.oficina.billing.support.CenarioBilling;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;

import java.math.BigDecimal;
import java.util.Map;

import static br.com.oficina.billing.domain.mensageria.TiposMensagem.*;
import static org.assertj.core.api.Assertions.assertThat;

public class PagamentoSteps {
    private CenarioBilling c;
    private Orcamento orcamento;
    private Pagamento pagamento;

    @Dado("que o orquestrador solicitou um orçamento com itens no total de {string}")
    public void orcamentoSolicitado(String total) {
        c = new CenarioBilling();
        orcamento = c.gerarOrcamento();
        assertThat(orcamento.getValorTotal()).isEqualByComparingTo(new BigDecimal(total));
    }

    @Quando("o cliente aprova o orçamento")
    public void clienteAprova() {
        pagamento = c.decisao.aprovar(orcamento.getId(), CenarioBilling.CPF);
    }

    @Quando("o cliente rejeita o orçamento")
    public void clienteRejeita() {
        c.decisao.rejeitar(orcamento.getId(), CenarioBilling.CPF, "Vou pensar");
    }

    @Quando("o Mercado Pago notifica o pagamento como {string}")
    public void notificacao(String resultado) {
        c.notificacao.processar(c.gateway.simular(pagamento, "aprovado".equals(resultado)));
    }

    @E("o orquestrador solicita o estorno do pagamento")
    public void estorno() {
        c.comando(CMD_ESTORNAR_PAGAMENTO, Map.of("pagamentoId", pagamento.getId().toString(), "motivo", "Estoque insuficiente"));
    }

    @Quando("o orquestrador solicita o cancelamento do orçamento")
    public void cancelamento() {
        c.comando(CMD_CANCELAR_ORCAMENTO, Map.of("orcamentoId", orcamento.getId().toString()));
    }

    @Então("deve ser publicado {string} com link de pagamento")
    public void publicadoComLink(String tipo) {
        assertThat(c.publicador.ultimo(tipo).texto("linkPagamento")).isNotBlank();
    }

    @Então("deve ser publicado {string}")
    public void publicado(String tipo) {
        assertThat(c.publicador.tipos()).contains(tipo);
    }

    @Então("o orçamento deve estar {string}")
    public void orcamentoEm(String status) {
        assertThat(orcamento.getStatus().name()).isEqualTo(status);
    }

    @Então("o pagamento deve estar {string}")
    public void pagamentoEm(String status) {
        assertThat(pagamento.getStatus().name()).isEqualTo(status);
    }
}
