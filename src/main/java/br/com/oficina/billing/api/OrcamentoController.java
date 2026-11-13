package br.com.oficina.billing.api;

import br.com.oficina.billing.api.dto.OrcamentoResponse;
import br.com.oficina.billing.api.dto.PagamentoResponse;
import br.com.oficina.billing.api.dto.RejeicaoRequest;
import br.com.oficina.billing.application.ConsultaBillingUseCase;
import br.com.oficina.billing.application.DecisaoOrcamentoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Orçamentos", description = "Consulta, aprovação (gera cobrança no Mercado Pago) e rejeição")
@RestController
@RequestMapping("/orcamentos")
@RequiredArgsConstructor
public class OrcamentoController {
    private final ConsultaBillingUseCase consulta;
    private final DecisaoOrcamentoUseCase decisao;

    @GetMapping("/{id}")
    public OrcamentoResponse buscar(@PathVariable UUID id, Authentication auth) {
        return OrcamentoResponse.de(consulta.orcamento(id, cpfDoCliente(auth)));
    }

    @GetMapping("/os/{osId}")
    public OrcamentoResponse buscarPorOs(@PathVariable UUID osId, Authentication auth) {
        return OrcamentoResponse.de(consulta.orcamentoDaOs(osId, cpfDoCliente(auth)));
    }

    @GetMapping("/{id}/pagamentos")
    public List<PagamentoResponse> pagamentos(@PathVariable UUID id, Authentication auth) {
        return consulta.pagamentos(id, cpfDoCliente(auth)).stream().map(PagamentoResponse::de).toList();
    }

    @Operation(summary = "Aprova o orçamento e cria a cobrança no Mercado Pago (retorna o link de pagamento)")
    @PostMapping("/{id}/aprovar")
    public PagamentoResponse aprovar(@PathVariable UUID id, Authentication auth) {
        return PagamentoResponse.de(decisao.aprovar(id, cpfDoCliente(auth)));
    }

    @Operation(summary = "Rejeita o orçamento (dispara a compensação da Saga)")
    @PostMapping("/{id}/rejeitar")
    public OrcamentoResponse rejeitar(@PathVariable UUID id, @Valid @RequestBody(required = false) RejeicaoRequest req,
                                      Authentication auth) {
        return OrcamentoResponse.de(decisao.rejeitar(id, cpfDoCliente(auth), req == null ? null : req.motivo()));
    }

    /** Token de cliente (Lambda de CPF) só acessa o próprio orçamento; funcionário acessa todos. */
    static String cpfDoCliente(Authentication auth) {
        boolean cliente = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE"));
        return cliente ? auth.getName() : null;
    }
}
