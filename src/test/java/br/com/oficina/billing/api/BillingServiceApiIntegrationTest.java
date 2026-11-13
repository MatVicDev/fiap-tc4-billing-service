package br.com.oficina.billing.api;

import br.com.oficina.billing.application.ProcessarComandoUseCase;
import br.com.oficina.billing.domain.mensageria.Mensagem;
import br.com.oficina.billing.infrastructure.gateway.FakeGatewayPagamento;
import br.com.oficina.billing.infrastructure.persistence.OutboxMensagem;
import br.com.oficina.billing.infrastructure.persistence.OutboxMensagemJpa;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.oficina.billing.domain.mensageria.TiposMensagem.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Testes de integração da API do billing-service")
class BillingServiceApiIntegrationTest {

    private static final String CPF = "52998224725";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private ProcessarComandoUseCase comandos;
    @Autowired
    private OutboxMensagemJpa outbox;
    @Autowired
    private FakeGatewayPagamento gateway;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private String token(String subject, String role) {
        return "Bearer " + Jwts.builder().subject(subject).claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("chave-de-teste-com-pelo-menos-32-caracteres-ok".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private UUID gerarOrcamento(UUID osId) {
        comandos.processar(Mensagem.nova(CMD_GERAR_ORCAMENTO, osId, "corr", Map.of(
                "clienteId", UUID.randomUUID().toString(), "nomeCliente", "Ana", "emailCliente", "ana@email.com",
                "cpfCliente", CPF,
                "itens", List.of(Map.of("tipo", "SERVICO", "referenciaId", UUID.randomUUID().toString(),
                        "descricao", "Revisão", "precoUnitario", "300.00", "quantidade", 1)))));
        return UUID.fromString(outboxDaOs(osId, EVT_ORCAMENTO_GERADO).getPayload()
                .replaceAll(".*\"orcamentoId\":\"([0-9a-f-]+)\".*", "$1"));
    }

    private OutboxMensagem outboxDaOs(UUID osId, String tipo) {
        return outbox.findAll().stream()
                .filter(m -> m.getRoutingKey().equals(tipo) && m.getPayload().contains(osId.toString()))
                .reduce((a, b) -> b).orElseThrow(() -> new AssertionError(tipo + " não está no outbox"));
    }

    @Test
    @DisplayName("Fluxo completo: cliente consulta, aprova, paga no checkout simulado; estorno como compensação")
    void fluxoCompleto() throws Exception {
        UUID osId = UUID.randomUUID();
        UUID orcamentoId = gerarOrcamento(osId);
        String cliente = token(CPF, "CLIENTE");

        mvc.perform(get("/orcamentos/os/" + osId).header("Authorization", cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.valorTotal").value(300.0));

        String resposta = mvc.perform(post("/orcamentos/" + orcamentoId + "/aprovar").header("Authorization", cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.linkPagamento").value(containsString("/checkout-simulado")))
                .andReturn().getResponse().getContentAsString();
        String pagamentoId = json.readTree(resposta).get("id").asString();
        assertThat(outboxDaOs(osId, EVT_ORCAMENTO_APROVADO).getPayload()).contains("linkPagamento");

        mvc.perform(get("/pagamentos/" + pagamentoId + "/checkout-simulado"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("R$ 300.00")));
        mvc.perform(post("/pagamentos/" + pagamentoId + "/simular").param("resultado", "APROVADO"))
                .andExpect(status().isOk())
                .andExpect(content().string("Pagamento APROVADO"));
        assertThat(outboxDaOs(osId, EVT_PAGAMENTO_CONFIRMADO).getPayload()).contains(pagamentoId);

        mvc.perform(get("/orcamentos/" + orcamentoId + "/pagamentos").header("Authorization", cliente))
                .andExpect(jsonPath("$[0].status").value("APROVADO"));

        comandos.processar(Mensagem.nova(CMD_ESTORNAR_PAGAMENTO, osId, "corr",
                Map.of("pagamentoId", pagamentoId, "motivo", "Estoque insuficiente")));
        mvc.perform(get("/orcamentos/" + orcamentoId).header("Authorization", token("admin", "ADMIN")))
                .andExpect(jsonPath("$.status").value("ESTORNADO"));
        assertThat(outboxDaOs(osId, EVT_PAGAMENTO_ESTORNADO).getPayload()).contains("300.00");
    }

    @Test
    @DisplayName("Webhook do Mercado Pago (formato Webhooks e IPN) processa o pagamento notificado")
    void webhook() throws Exception {
        UUID osId = UUID.randomUUID();
        UUID orcamentoId = gerarOrcamento(osId);
        String resposta = mvc.perform(post("/orcamentos/" + orcamentoId + "/aprovar").header("Authorization", token("admin", "ADMIN")))
                .andReturn().getResponse().getContentAsString();
        UUID pagamentoId = UUID.fromString(json.readTree(resposta).get("id").asString());
        br.com.oficina.billing.domain.pagamento.Pagamento p =
                new br.com.oficina.billing.domain.pagamento.Pagamento(orcamentoId, osId, java.math.BigDecimal.TEN);
        // Simula o resultado no "provedor" para o id de pagamento real criado acima.
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", pagamentoId);
        String gatewayId = gateway.simular(p, false);

        mvc.perform(post("/webhooks/mercadopago").param("type", "merchant_order").param("data.id", "1"))
                .andExpect(status().isOk());
        mvc.perform(post("/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment\",\"data\":{\"id\":\"" + gatewayId + "\"}}"))
                .andExpect(status().isOk());
        assertThat(outboxDaOs(osId, EVT_PAGAMENTO_RECUSADO).getPayload()).contains("cc_rejected_other_reason");

        mvc.perform(post("/webhooks/mercadopago").param("topic", "payment").param("id", gatewayId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Rejeição, acesso de outro cliente (403), não encontrado (404) e transição inválida (422)")
    void regrasDeAcessoEErros() throws Exception {
        UUID osId = UUID.randomUUID();
        UUID orcamentoId = gerarOrcamento(osId);

        mvc.perform(get("/orcamentos/" + orcamentoId).header("Authorization", token("11144477735", "CLIENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/orcamentos/" + orcamentoId)).andExpect(status().isForbidden());
        mvc.perform(get("/orcamentos/" + UUID.randomUUID()).header("Authorization", token("admin", "ADMIN")))
                .andExpect(status().isNotFound());

        mvc.perform(post("/orcamentos/" + orcamentoId + "/rejeitar").header("Authorization", token(CPF, "CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Caro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJEITADO"));
        mvc.perform(post("/orcamentos/" + orcamentoId + "/aprovar").header("Authorization", token(CPF, "CLIENTE")))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get("/pagamentos/" + UUID.randomUUID() + "/checkout-simulado")).andExpect(status().isNotFound());
    }
}
