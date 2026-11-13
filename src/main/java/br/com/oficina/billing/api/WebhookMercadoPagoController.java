package br.com.oficina.billing.api;

import br.com.oficina.billing.application.ProcessarNotificacaoPagamentoUseCase;
import br.com.oficina.billing.infrastructure.gateway.AssinaturaWebhookMercadoPago;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Tag(name = "Webhooks", description = "Notificações do Mercado Pago")
@RestController
@RequestMapping("/webhooks/mercadopago")
@RequiredArgsConstructor
public class WebhookMercadoPagoController {
    private final ProcessarNotificacaoPagamentoUseCase useCase;
    private final AssinaturaWebhookMercadoPago assinatura;

    /**
     * Aceita o formato Webhooks ({@code ?type=payment&data.id=...} + corpo JSON) e o IPN legado
     * ({@code ?topic=payment&id=...}). Notificações de outros tópicos recebem 200 e são ignoradas.
     */
    @SecurityRequirements
    @Operation(summary = "Recebe notificação de pagamento do Mercado Pago")
    @PostMapping
    public ResponseEntity<Void> receber(@RequestParam(name = "type", required = false) String type,
                                        @RequestParam(name = "topic", required = false) String topic,
                                        @RequestParam(name = "data.id", required = false) String dataIdQuery,
                                        @RequestParam(name = "id", required = false) String idQuery,
                                        @RequestHeader(name = "x-signature", required = false) String xSignature,
                                        @RequestHeader(name = "x-request-id", required = false) String xRequestId,
                                        @RequestBody(required = false) Map<String, Object> corpo) {
        String tipo = type != null ? type : topic != null ? topic : corpo == null ? null : (String) corpo.get("type");
        String dataId = dataIdQuery != null ? dataIdQuery : idQuery != null ? idQuery : idDoCorpo(corpo);

        if (!assinatura.valida(xSignature, xRequestId, dataId)) {
            log.warn("Notificação do Mercado Pago com assinatura inválida (data.id={})", dataId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"payment".equals(tipo) || dataId == null) {
            log.info("Notificação do Mercado Pago ignorada (tipo={}, id={})", tipo, dataId);
            return ResponseEntity.ok().build();
        }
        useCase.processar(dataId);
        return ResponseEntity.ok().build();
    }

    @SuppressWarnings("unchecked")
    private static String idDoCorpo(Map<String, Object> corpo) {
        if (corpo != null && corpo.get("data") instanceof Map<?, ?> data && data.get("id") != null) {
            return String.valueOf(((Map<String, Object>) data).get("id"));
        }
        return null;
    }
}
