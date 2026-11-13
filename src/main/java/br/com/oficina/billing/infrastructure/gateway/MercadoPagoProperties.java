package br.com.oficina.billing.infrastructure.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param modo            "mercadopago" (API real) ou "fake" (simulação local/CI, sem credenciais)
 * @param accessToken     Access Token da aplicação (credenciais de teste do painel de desenvolvedor)
 * @param webhookSecret   Assinatura secreta das notificações (Webhooks → "Assinatura secreta")
 * @param notificationUrl URL pública de /webhooks/mercadopago (ALB do EKS ou túnel local)
 * @param urlPublica      URL pública do billing-service (usada nos links do modo fake e back_urls)
 */
@ConfigurationProperties(prefix = "mercadopago")
public record MercadoPagoProperties(
        String modo,
        String apiUrl,
        String accessToken,
        String webhookSecret,
        String notificationUrl,
        String urlPublica
) {
    public MercadoPagoProperties {
        modo = modo == null || modo.isBlank() ? "fake" : modo;
        apiUrl = apiUrl == null || apiUrl.isBlank() ? "https://api.mercadopago.com" : apiUrl;
        urlPublica = urlPublica == null || urlPublica.isBlank() ? "http://localhost:8081" : urlPublica;
    }

    public boolean usarSandbox() {
        return accessToken != null && accessToken.startsWith("TEST-");
    }
}
