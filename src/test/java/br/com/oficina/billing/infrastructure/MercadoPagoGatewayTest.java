package br.com.oficina.billing.infrastructure;

import br.com.oficina.billing.domain.orcamento.ItemOrcamento;
import br.com.oficina.billing.domain.orcamento.Orcamento;
import br.com.oficina.billing.domain.pagamento.GatewayPagamento;
import br.com.oficina.billing.domain.pagamento.Pagamento;
import br.com.oficina.billing.infrastructure.gateway.GatewayPagamentoException;
import br.com.oficina.billing.infrastructure.gateway.MercadoPagoGateway;
import br.com.oficina.billing.infrastructure.gateway.MercadoPagoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Testes da integração com a API do Mercado Pago")
class MercadoPagoGatewayTest {

    private final Orcamento orcamento = new Orcamento(UUID.randomUUID(), UUID.randomUUID(), "Ana", "ana@email.com", "1",
            List.of(new ItemOrcamento("SERVICO", null, "Revisão", new BigDecimal("150.00"), 1)));
    private final Pagamento pagamento = new Pagamento(orcamento.getId(), orcamento.getOsId(), orcamento.getValorTotal());

    private record Ctx(MercadoPagoGateway gateway, MockRestServiceServer server) {
    }

    private Ctx criar(String token) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoProperties props = new MercadoPagoProperties("mercadopago", "https://api.mp.test", token, null,
                "https://oficina/webhooks/mercadopago", null);
        return new Ctx(new MercadoPagoGateway(builder, props), server);
    }

    @Test
    @DisplayName("Cria preferência com external_reference e notification_url; usa sandbox com token TEST-")
    void criarPreferencia() {
        Ctx c = criar("TEST-123");
        c.server.expect(requestTo("https://api.mp.test/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-123"))
                .andExpect(jsonPath("$.external_reference").value(pagamento.getId().toString()))
                .andExpect(jsonPath("$.notification_url").value("https://oficina/webhooks/mercadopago"))
                .andExpect(jsonPath("$.items[0].title").value("Revisão"))
                .andExpect(jsonPath("$.items[0].currency_id").value("BRL"))
                .andExpect(jsonPath("$.payer.email").value("ana@email.com"))
                .andRespond(withSuccess("{\"id\":\"pref-1\",\"init_point\":\"https://mp/prod\",\"sandbox_init_point\":\"https://mp/sandbox\"}",
                        MediaType.APPLICATION_JSON));

        GatewayPagamento.Cobranca cobranca = c.gateway.criarCobranca(pagamento, orcamento);

        assertThat(cobranca.gatewayCobrancaId()).isEqualTo("pref-1");
        assertThat(cobranca.linkPagamento()).isEqualTo("https://mp/sandbox");
        c.server.verify();
    }

    @Test
    @DisplayName("Com credencial de produção/usuário de teste usa init_point")
    void initPoint() {
        Ctx c = criar("APP_USR-1");
        c.server.expect(requestTo("https://api.mp.test/checkout/preferences"))
                .andRespond(withSuccess("{\"id\":\"pref-2\",\"init_point\":\"https://mp/prod\"}", MediaType.APPLICATION_JSON));
        assertThat(c.gateway.criarCobranca(pagamento, orcamento).linkPagamento()).isEqualTo("https://mp/prod");
    }

    @Test
    @DisplayName("Consulta pagamento e traduz status do Mercado Pago")
    void consultar() {
        Ctx c = criar("TEST-1");
        c.server.expect(requestTo("https://api.mp.test/v1/payments/999"))
                .andRespond(withSuccess("{\"id\":999,\"status\":\"approved\",\"status_detail\":\"accredited\","
                        + "\"external_reference\":\"" + pagamento.getId() + "\",\"transaction_amount\":150.0,\"outro\":1}",
                        MediaType.APPLICATION_JSON));

        GatewayPagamento.ConsultaPagamento consulta = c.gateway.consultar("999");

        assertThat(consulta.situacao()).isEqualTo(GatewayPagamento.Situacao.APROVADO);
        assertThat(consulta.referenciaExterna()).isEqualTo(pagamento.getId().toString());
        assertThat(consulta.valor()).isEqualByComparingTo("150");
    }

    @Test
    @DisplayName("Estorno envia X-Idempotency-Key")
    void estornar() {
        Ctx c = criar("TEST-1");
        c.server.expect(requestTo("https://api.mp.test/v1/payments/999/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "estorno-abc"))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));
        c.gateway.estornar("999", BigDecimal.TEN, "estorno-abc");
        c.server.verify();
    }

    @Test
    @DisplayName("Erros HTTP do provedor viram GatewayPagamentoException")
    void erros() {
        Ctx c = criar("TEST-1");
        c.server.expect(requestTo("https://api.mp.test/checkout/preferences")).andRespond(withServerError());
        c.server.expect(requestTo("https://api.mp.test/v1/payments/1")).andRespond(withServerError());
        c.server.expect(requestTo("https://api.mp.test/v1/payments/1/refunds")).andRespond(withServerError());
        assertThatThrownBy(() -> c.gateway.criarCobranca(pagamento, orcamento)).isInstanceOf(GatewayPagamentoException.class);
        assertThatThrownBy(() -> c.gateway.consultar("1")).isInstanceOf(GatewayPagamentoException.class);
        assertThatThrownBy(() -> c.gateway.estornar("1", BigDecimal.ONE, "k")).isInstanceOf(GatewayPagamentoException.class);
    }
}
