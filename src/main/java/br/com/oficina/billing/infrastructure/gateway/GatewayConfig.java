package br.com.oficina.billing.infrastructure.gateway;

import br.com.oficina.billing.domain.pagamento.GatewayPagamento;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@EnableConfigurationProperties(MercadoPagoProperties.class)
public class GatewayConfig {

    @Bean
    @ConditionalOnProperty(name = "mercadopago.modo", havingValue = "mercadopago")
    GatewayPagamento mercadoPagoGateway(RestClient.Builder builder, MercadoPagoProperties props) {
        if (props.accessToken() == null || props.accessToken().isBlank()) {
            throw new IllegalStateException("mercadopago.modo=mercadopago exige MERCADOPAGO_ACCESS_TOKEN");
        }
        log.info("Gateway de pagamento: Mercado Pago ({})", props.usarSandbox() ? "sandbox" : "credenciais de produção/usuário de teste");
        return new MercadoPagoGateway(builder, props);
    }

    @Bean
    @ConditionalOnProperty(name = "mercadopago.modo", havingValue = "fake", matchIfMissing = true)
    FakeGatewayPagamento fakeGatewayPagamento(MercadoPagoProperties props) {
        log.warn("Gateway de pagamento: SIMULADO (mercadopago.modo=fake) — use apenas em dev/CI");
        return new FakeGatewayPagamento(props);
    }

    @Bean
    AssinaturaWebhookMercadoPago assinaturaWebhookMercadoPago(MercadoPagoProperties props) {
        return new AssinaturaWebhookMercadoPago(props.webhookSecret());
    }
}
