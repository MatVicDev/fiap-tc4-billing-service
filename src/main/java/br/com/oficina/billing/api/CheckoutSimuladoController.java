package br.com.oficina.billing.api;

import br.com.oficina.billing.application.ProcessarNotificacaoPagamentoUseCase;
import br.com.oficina.billing.domain.exception.EntidadeNaoEncontradaException;
import br.com.oficina.billing.domain.pagamento.Pagamento;
import br.com.oficina.billing.domain.pagamento.PagamentoRepository;
import br.com.oficina.billing.infrastructure.gateway.FakeGatewayPagamento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Substitui a página de checkout do Mercado Pago quando {@code mercadopago.modo=fake}:
 * permite aprovar/recusar e segue exatamente o mesmo caminho de uma notificação real.
 */
@Tag(name = "Checkout simulado", description = "Disponível apenas com mercadopago.modo=fake")
@RestController
@RequestMapping("/pagamentos/{id}")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mercadopago.modo", havingValue = "fake", matchIfMissing = true)
public class CheckoutSimuladoController {
    private final FakeGatewayPagamento gateway;
    private final PagamentoRepository pagamentoRepository;
    private final ProcessarNotificacaoPagamentoUseCase notificacao;

    @SecurityRequirements
    @GetMapping(value = "/checkout-simulado", produces = MediaType.TEXT_HTML_VALUE)
    public String pagina(@PathVariable UUID id) {
        Pagamento p = buscar(id);
        return """
                <!doctype html><html lang="pt-br"><head><meta charset="utf-8"><title>Checkout simulado</title></head>
                <body style="font-family:sans-serif;max-width:480px;margin:40px auto">
                <h2>Checkout simulado (Mercado Pago fake)</h2>
                <p>Pagamento <code>%s</code><br>Valor: <b>R$ %s</b><br>Status: %s</p>
                <form method="post" action="simular?resultado=APROVADO"><button>Aprovar pagamento</button></form><br>
                <form method="post" action="simular?resultado=RECUSADO"><button>Recusar pagamento</button></form>
                </body></html>""".formatted(p.getId(), p.getValor().toPlainString(), p.getStatus());
    }

    @SecurityRequirements
    @Operation(summary = "Simula o resultado do pagamento e processa como se fosse o webhook do Mercado Pago")
    @PostMapping("/simular")
    public String simular(@PathVariable UUID id, @RequestParam String resultado) {
        Pagamento p = buscar(id);
        String gatewayId = gateway.simular(p, "APROVADO".equalsIgnoreCase(resultado));
        return notificacao.processar(gatewayId).map(pg -> "Pagamento " + pg.getStatus()).orElse("Ignorado");
    }

    private Pagamento buscar(UUID id) {
        return pagamentoRepository.buscarPorId(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Pagamento não encontrado"));
    }
}
