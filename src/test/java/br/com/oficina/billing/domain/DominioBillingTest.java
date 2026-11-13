package br.com.oficina.billing.domain;

import br.com.oficina.billing.domain.exception.DomainException;
import br.com.oficina.billing.domain.orcamento.ItemOrcamento;
import br.com.oficina.billing.domain.orcamento.Orcamento;
import br.com.oficina.billing.domain.orcamento.StatusOrcamento;
import br.com.oficina.billing.domain.pagamento.Pagamento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Testes do domínio do billing-service")
class DominioBillingTest {

    private final ItemOrcamento item = new ItemOrcamento("SERVICO", null, "Revisão", BigDecimal.TEN, 1);

    @Test
    @DisplayName("Validações do orçamento e dos itens")
    void validacoes() {
        assertThatThrownBy(() -> new Orcamento(null, null, null, null, null, List.of(item))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Orcamento(UUID.randomUUID(), null, null, null, null, List.of())).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Orcamento(UUID.randomUUID(), null, null, null, null,
                List.of(new ItemOrcamento("SERVICO", null, "x", BigDecimal.ZERO, 1)))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new ItemOrcamento("SERVICO", null, "x", BigDecimal.ONE, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItemOrcamento("SERVICO", null, "x", new BigDecimal("-1"), 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Ciclo de vida do orçamento")
    void cicloOrcamento() {
        Orcamento o = new Orcamento(UUID.randomUUID(), UUID.randomUUID(), "Ana", "a@a.com", "123", List.of(item));
        assertThat(o.pertenceAoCpf("123")).isTrue();
        assertThat(o.pertenceAoCpf("999")).isFalse();
        o.registrarPagamento();
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PENDENTE);
        o.aprovar();
        o.registrarPagamento();
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PAGO);
        assertThat(o.cancelar("x")).isFalse();
        o.registrarEstorno("falha");
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.ESTORNADO);
        assertThat(o.cancelar("x")).isFalse();
        assertThatThrownBy(() -> o.rejeitar(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Ciclo de vida do pagamento")
    void cicloPagamento() {
        Pagamento p = new Pagamento(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);
        assertThatThrownBy(p::estornar).isInstanceOf(IllegalStateException.class);
        p.registrarCobranca("pref", "http://link");
        p.aprovar("mp-1", "accredited");
        assertThatThrownBy(() -> p.recusar("mp-1", null)).isInstanceOf(IllegalStateException.class);
        p.estornar();
        assertThat(p.getStatus().isFinal()).isTrue();
        assertThat(p.getAtualizadoEm()).isNotNull();
        assertThat(p.getGatewayCobrancaId()).isEqualTo("pref");
    }
}
