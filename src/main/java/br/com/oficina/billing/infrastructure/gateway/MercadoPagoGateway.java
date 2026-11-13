package br.com.oficina.billing.infrastructure.gateway;

import br.com.oficina.billing.domain.orcamento.Orcamento;
import br.com.oficina.billing.domain.pagamento.GatewayPagamento;
import br.com.oficina.billing.domain.pagamento.Pagamento;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Integração com a API do Mercado Pago (Checkout Pro):
 * <ul>
 *   <li>POST /checkout/preferences — cria a cobrança com external_reference = id do pagamento</li>
 *   <li>GET /v1/payments/{id} — consulta o pagamento notificado pelo webhook</li>
 *   <li>POST /v1/payments/{id}/refunds — estorno total (compensação da Saga), com X-Idempotency-Key</li>
 * </ul>
 */
@Slf4j
public class MercadoPagoGateway implements GatewayPagamento {
    private final RestClient client;
    private final MercadoPagoProperties props;

    public MercadoPagoGateway(RestClient.Builder builder, MercadoPagoProperties props) {
        this.props = props;
        this.client = builder.baseUrl(props.apiUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.accessToken())
                .build();
    }

    @Override
    public Cobranca criarCobranca(Pagamento pagamento, Orcamento orcamento) {
        Map<String, Object> body = new HashMap<>();
        body.put("external_reference", pagamento.getId().toString());
        body.put("statement_descriptor", "OFICINA");
        body.put("metadata", Map.of("os_id", orcamento.getOsId().toString(), "orcamento_id", orcamento.getId().toString()));
        body.put("items", orcamento.getItens().stream().map(i -> Map.of(
                "title", i.getDescricao(),
                "quantity", i.getQuantidade(),
                "unit_price", i.getPrecoUnitario(),
                "currency_id", "BRL")).toList());
        if (orcamento.getEmailCliente() != null && !orcamento.getEmailCliente().isBlank()) {
            body.put("payer", Map.of("email", orcamento.getEmailCliente(), "name", String.valueOf(orcamento.getNomeCliente())));
        }
        if (props.notificationUrl() != null && !props.notificationUrl().isBlank()) {
            body.put("notification_url", props.notificationUrl());
        }
        try {
            Preferencia pref = client.post().uri("/checkout/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Preferencia.class);
            String link = props.usarSandbox() && pref.sandboxInitPoint() != null ? pref.sandboxInitPoint() : pref.initPoint();
            log.info("Preferência {} criada no Mercado Pago para o pagamento {}", pref.id(), pagamento.getId());
            return new Cobranca(pref.id(), link);
        } catch (RestClientException e) {
            throw new GatewayPagamentoException("Falha ao criar cobrança no Mercado Pago", e);
        }
    }

    @Override
    public ConsultaPagamento consultar(String gatewayPagamentoId) {
        try {
            PagamentoMp mp = client.get().uri("/v1/payments/{id}", gatewayPagamentoId).retrieve().body(PagamentoMp.class);
            return new ConsultaPagamento(String.valueOf(mp.id()), situacao(mp.status()), mp.statusDetail(),
                    mp.externalReference(), mp.transactionAmount());
        } catch (RestClientException e) {
            throw new GatewayPagamentoException("Falha ao consultar pagamento " + gatewayPagamentoId + " no Mercado Pago", e);
        }
    }

    @Override
    public void estornar(String gatewayPagamentoId, BigDecimal valor, String chaveIdempotencia) {
        try {
            client.post().uri("/v1/payments/{id}/refunds", gatewayPagamentoId)
                    .header("X-Idempotency-Key", chaveIdempotencia)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .toBodilessEntity();
            log.warn("Estorno total do pagamento {} (R$ {}) solicitado ao Mercado Pago", gatewayPagamentoId, valor);
        } catch (RestClientException e) {
            throw new GatewayPagamentoException("Falha ao estornar pagamento " + gatewayPagamentoId + " no Mercado Pago", e);
        }
    }

    static Situacao situacao(String statusMp) {
        if (statusMp == null) {
            return Situacao.PENDENTE;
        }
        return switch (statusMp) {
            case "approved" -> Situacao.APROVADO;
            case "rejected", "cancelled", "refunded", "charged_back" -> Situacao.RECUSADO;
            default -> Situacao.PENDENTE; // pending, in_process, authorized, in_mediation
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Preferencia(String id,
                       @JsonProperty("init_point") String initPoint,
                       @JsonProperty("sandbox_init_point") String sandboxInitPoint) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PagamentoMp(Long id,
                       String status,
                       @JsonProperty("status_detail") String statusDetail,
                       @JsonProperty("external_reference") String externalReference,
                       @JsonProperty("transaction_amount") BigDecimal transactionAmount,
                       List<Object> refunds) {
    }
}
