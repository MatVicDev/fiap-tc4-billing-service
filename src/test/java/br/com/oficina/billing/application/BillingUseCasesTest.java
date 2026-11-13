package br.com.oficina.billing.application;

import br.com.oficina.billing.domain.exception.EntidadeNaoEncontradaException;
import br.com.oficina.billing.domain.orcamento.Orcamento;
import br.com.oficina.billing.domain.orcamento.StatusOrcamento;
import br.com.oficina.billing.domain.pagamento.Pagamento;
import br.com.oficina.billing.domain.pagamento.StatusPagamento;
import br.com.oficina.billing.support.CenarioBilling;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static br.com.oficina.billing.domain.mensageria.TiposMensagem.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Testes dos casos de uso do billing-service")
class BillingUseCasesTest {

    @Test
    @DisplayName("gerar-orcamento cria o orçamento com o total dos itens e é idempotente por OS")
    void gerarOrcamento() {
        CenarioBilling c = new CenarioBilling();
        Orcamento o = c.gerarOrcamento();
        c.gerarOrcamento();

        assertThat(o.getValorTotal()).isEqualByComparingTo(new BigDecimal("291.00"));
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PENDENTE);
        assertThat(c.orcamentos.dados).hasSize(1);
        assertThat(c.publicador.tipos()).containsExactly(EVT_ORCAMENTO_GERADO, EVT_ORCAMENTO_GERADO);
        assertThat(c.publicador.ultimo(EVT_ORCAMENTO_GERADO).texto("orcamentoId")).isEqualTo(o.getId().toString());
    }

    @Test
    @DisplayName("Aprovar cria a cobrança e publica o link de pagamento")
    void aprovar() {
        CenarioBilling c = new CenarioBilling();
        Orcamento o = c.gerarOrcamento();

        Pagamento p = c.decisao.aprovar(o.getId(), CenarioBilling.CPF);

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(p.getLinkPagamento()).isEqualTo("http://billing/pagamentos/" + p.getId() + "/checkout-simulado");
        assertThat(c.publicador.ultimo(EVT_ORCAMENTO_APROVADO).texto("linkPagamento")).isEqualTo(p.getLinkPagamento());
    }

    @Test
    @DisplayName("Cliente não decide sobre orçamento de outro CPF")
    void outroCliente() {
        CenarioBilling c = new CenarioBilling();
        UUID id = c.gerarOrcamento().getId();
        assertThatThrownBy(() -> c.decisao.aprovar(id, "98765432100")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> c.decisao.aprovar(UUID.randomUUID(), null)).isInstanceOf(EntidadeNaoEncontradaException.class);
    }

    @Test
    @DisplayName("Rejeitar publica orcamento-rejeitado")
    void rejeitar() {
        CenarioBilling c = new CenarioBilling();
        Orcamento o = c.gerarOrcamento();
        c.decisao.rejeitar(o.getId(), null, "Muito caro");
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.REJEITADO);
        assertThat(c.publicador.ultimo(EVT_ORCAMENTO_REJEITADO).texto("motivo")).isEqualTo("Muito caro");
        UUID id = o.getId();
        assertThatThrownBy(() -> c.decisao.aprovar(id, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Notificação aprovada confirma o pagamento e o orçamento fica PAGO (idempotente)")
    void pagamentoAprovado() {
        CenarioBilling c = new CenarioBilling();
        Pagamento p = c.aprovarEPagar(true);

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(c.orcamentos.buscarPorId(p.getOrcamentoId()).orElseThrow().getStatus()).isEqualTo(StatusOrcamento.PAGO);
        assertThat(c.publicador.ultimo(EVT_PAGAMENTO_CONFIRMADO).texto("pagamentoId")).isEqualTo(p.getId().toString());

        c.notificacao.processar(p.getGatewayPagamentoId());
        assertThat(c.publicador.tipos().stream().filter(EVT_PAGAMENTO_CONFIRMADO::equals)).hasSize(1);
    }

    @Test
    @DisplayName("Notificação recusada publica pagamento-recusado com o motivo do provedor")
    void pagamentoRecusado() {
        CenarioBilling c = new CenarioBilling();
        Pagamento p = c.aprovarEPagar(false);
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(c.publicador.ultimo(EVT_PAGAMENTO_RECUSADO).texto("motivo")).isEqualTo("cc_rejected_other_reason");
    }

    @Test
    @DisplayName("Notificação desconhecida ou pendente é ignorada")
    void notificacaoIgnorada() {
        CenarioBilling c = new CenarioBilling();
        assertThat(c.notificacao.processar("inexistente")).isEmpty();
        Orcamento o = c.gerarOrcamento();
        Pagamento p = c.decisao.aprovar(o.getId(), null);
        int antes = c.publicador.eventos.size();
        assertThat(c.notificacao.processar("fake-" + p.getId())).isEmpty();
        assertThat(c.publicador.eventos).hasSize(antes);
    }

    @Test
    @DisplayName("estornar-pagamento estorna no provedor, marca ESTORNADO e confirma ao orquestrador")
    void estorno() {
        CenarioBilling c = new CenarioBilling();
        Pagamento p = c.aprovarEPagar(true);

        c.comando(CMD_ESTORNAR_PAGAMENTO, Map.of("pagamentoId", p.getId().toString(), "motivo", "Estoque insuficiente"));

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        Orcamento o = c.orcamentos.buscarPorId(p.getOrcamentoId()).orElseThrow();
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.ESTORNADO);
        assertThat(o.getMotivo()).isEqualTo("Estoque insuficiente");
        assertThat(c.publicador.ultimo(EVT_PAGAMENTO_ESTORNADO).texto("valorEstornado")).isEqualTo("291.00");

        c.comando(CMD_ESTORNAR_PAGAMENTO, Map.of("pagamentoId", p.getId().toString()));
        assertThat(c.publicador.ultimo(EVT_PAGAMENTO_ESTORNADO).texto("observacao")).contains("ESTORNADO");
    }

    @Test
    @DisplayName("estornar-pagamento sem pagamento conhecido apenas confirma")
    void estornoSemPagamento() {
        CenarioBilling c = new CenarioBilling();
        c.comando(CMD_ESTORNAR_PAGAMENTO, Map.of("pagamentoId", UUID.randomUUID().toString()));
        assertThat(c.publicador.ultimo(EVT_PAGAMENTO_ESTORNADO).texto("observacao")).contains("não encontrado");
    }

    @Test
    @DisplayName("cancelar-orcamento cancela pendente/aprovado, preserva pago e confirma sempre")
    void cancelar() {
        CenarioBilling c = new CenarioBilling();
        Orcamento o = c.gerarOrcamento();
        c.comando(CMD_CANCELAR_ORCAMENTO, Map.of("orcamentoId", o.getId().toString(), "motivo", "Prazo expirado"));
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(c.publicador.ultimo(EVT_ORCAMENTO_CANCELADO).texto("alterado")).isEqualTo("true");

        CenarioBilling pago = new CenarioBilling();
        Pagamento p = pago.aprovarEPagar(true);
        pago.comando(CMD_CANCELAR_ORCAMENTO, Map.of());
        assertThat(pago.orcamentos.buscarPorId(p.getOrcamentoId()).orElseThrow().getStatus()).isEqualTo(StatusOrcamento.PAGO);
        assertThat(pago.publicador.ultimo(EVT_ORCAMENTO_CANCELADO).texto("status")).isEqualTo("PAGO");

        CenarioBilling vazio = new CenarioBilling();
        vazio.comando(CMD_CANCELAR_ORCAMENTO, Map.of());
        assertThat(vazio.publicador.ultimo(EVT_ORCAMENTO_CANCELADO).texto("status")).isEqualTo("INEXISTENTE");
    }

    @Test
    @DisplayName("Comando duplicado ou desconhecido é ignorado")
    void duplicado() {
        CenarioBilling c = new CenarioBilling();
        br.com.oficina.billing.domain.mensageria.Mensagem m = br.com.oficina.billing.domain.mensageria.Mensagem
                .nova(CMD_CANCELAR_ORCAMENTO, c.osId, null, Map.of());
        c.comandos.processar(m);
        c.comandos.processar(m);
        c.comando("billing.outro", Map.of());
        assertThat(c.publicador.eventos).hasSize(1);
    }
}
