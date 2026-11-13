package br.com.oficina.billing.infrastructure.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Testes da validação de assinatura do webhook do Mercado Pago")
class AssinaturaWebhookMercadoPagoTest {

    private final AssinaturaWebhookMercadoPago assinatura = new AssinaturaWebhookMercadoPago("segredo");

    @Test
    @DisplayName("Aceita assinatura calculada sobre id;request-id;ts")
    void valida() {
        String v1 = assinatura.hmac("id:123abc;request-id:req-1;ts:1700000000;");
        assertThat(assinatura.valida("ts=1700000000,v1=" + v1, "req-1", "123ABC")).isTrue();
    }

    @Test
    @DisplayName("Rejeita assinatura adulterada, ausente ou malformada")
    void rejeita() {
        assertThat(assinatura.valida("ts=1700000000,v1=deadbeef", "req-1", "123")).isFalse();
        assertThat(assinatura.valida(null, "req-1", "123")).isFalse();
        assertThat(assinatura.valida("formato-invalido", "req-1", "123")).isFalse();
        assertThat(assinatura.valida("ts=1", null, null)).isFalse();
    }

    @Test
    @DisplayName("Sem request-id, o trecho é omitido do manifesto")
    void semRequestId() {
        String v1 = assinatura.hmac("id:9;ts:5;");
        assertThat(assinatura.valida("ts=5,v1=" + v1, null, "9")).isTrue();
    }

    @Test
    @DisplayName("Sem secret configurado, a validação fica desligada")
    void desligada() {
        AssinaturaWebhookMercadoPago semSecret = new AssinaturaWebhookMercadoPago("");
        assertThat(semSecret.habilitada()).isFalse();
        assertThat(semSecret.valida(null, null, null)).isTrue();
    }

    @Test
    @DisplayName("Mapeamento de status do Mercado Pago")
    void status() {
        assertThat(MercadoPagoGateway.situacao("approved")).hasToString("APROVADO");
        assertThat(MercadoPagoGateway.situacao("rejected")).hasToString("RECUSADO");
        assertThat(MercadoPagoGateway.situacao("cancelled")).hasToString("RECUSADO");
        assertThat(MercadoPagoGateway.situacao("in_process")).hasToString("PENDENTE");
        assertThat(MercadoPagoGateway.situacao(null)).hasToString("PENDENTE");
    }
}
