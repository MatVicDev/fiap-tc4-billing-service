package br.com.oficina.billing.application;

import br.com.oficina.billing.domain.exception.EntidadeNaoEncontradaException;
import br.com.oficina.billing.domain.mensageria.Mensagem;
import br.com.oficina.billing.domain.mensageria.PublicadorMensagens;
import br.com.oficina.billing.domain.orcamento.Orcamento;
import br.com.oficina.billing.domain.orcamento.OrcamentoRepository;
import br.com.oficina.billing.domain.pagamento.GatewayPagamento;
import br.com.oficina.billing.domain.pagamento.Pagamento;
import br.com.oficina.billing.domain.pagamento.PagamentoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static br.com.oficina.billing.domain.mensageria.TiposMensagem.EVT_ORCAMENTO_APROVADO;
import static br.com.oficina.billing.domain.mensageria.TiposMensagem.EVT_ORCAMENTO_REJEITADO;

/**
 * Decisão do cliente sobre o orçamento. Aprovar já cria a cobrança no Mercado Pago e
 * devolve o link de pagamento, que segue no evento para o orquestrador notificar o cliente.
 */
@Service
@RequiredArgsConstructor
public class DecisaoOrcamentoUseCase {
    private final OrcamentoRepository orcamentoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final GatewayPagamento gateway;
    private final PublicadorMensagens publicador;

    /**
     * @param cpfCliente CPF do token quando quem decide é o próprio cliente (role CLIENTE);
     *                   null quando é um funcionário (ADMIN) registrando a decisão.
     */
    @Transactional
    public Pagamento aprovar(UUID orcamentoId, String cpfCliente) {
        Orcamento orcamento = buscar(orcamentoId, cpfCliente);
        orcamento.aprovar();

        Pagamento pagamento = new Pagamento(orcamento.getId(), orcamento.getOsId(), orcamento.getValorTotal());
        GatewayPagamento.Cobranca cobranca = gateway.criarCobranca(pagamento, orcamento);
        pagamento.registrarCobranca(cobranca.gatewayCobrancaId(), cobranca.linkPagamento());
        pagamentoRepository.salvar(pagamento);
        orcamentoRepository.salvar(orcamento);

        Map<String, Object> dados = new HashMap<>();
        dados.put("orcamentoId", orcamento.getId().toString());
        dados.put("pagamentoId", pagamento.getId().toString());
        dados.put("linkPagamento", cobranca.linkPagamento());
        publicador.publicarEvento(Mensagem.nova(EVT_ORCAMENTO_APROVADO, orcamento.getOsId(), MDC.get("correlationId"), dados));
        return pagamento;
    }

    @Transactional
    public Orcamento rejeitar(UUID orcamentoId, String cpfCliente, String motivo) {
        Orcamento orcamento = buscar(orcamentoId, cpfCliente);
        orcamento.rejeitar(motivo);
        orcamentoRepository.salvar(orcamento);
        publicador.publicarEvento(Mensagem.nova(EVT_ORCAMENTO_REJEITADO, orcamento.getOsId(), MDC.get("correlationId"),
                Map.of("orcamentoId", orcamento.getId().toString(), "motivo", motivo == null ? "" : motivo)));
        return orcamento;
    }

    private Orcamento buscar(UUID id, String cpfCliente) {
        Orcamento orcamento = orcamentoRepository.buscarPorId(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Orçamento não encontrado"));
        if (cpfCliente != null && !orcamento.pertenceAoCpf(cpfCliente)) {
            throw new AccessDeniedException("Orçamento pertence a outro cliente.");
        }
        return orcamento;
    }
}
