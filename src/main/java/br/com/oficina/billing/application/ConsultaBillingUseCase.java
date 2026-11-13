package br.com.oficina.billing.application;

import br.com.oficina.billing.domain.exception.EntidadeNaoEncontradaException;
import br.com.oficina.billing.domain.orcamento.Orcamento;
import br.com.oficina.billing.domain.orcamento.OrcamentoRepository;
import br.com.oficina.billing.domain.pagamento.Pagamento;
import br.com.oficina.billing.domain.pagamento.PagamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaBillingUseCase {
    private final OrcamentoRepository orcamentoRepository;
    private final PagamentoRepository pagamentoRepository;

    public Orcamento orcamento(UUID id, String cpfCliente) {
        return verificarDono(orcamentoRepository.buscarPorId(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Orçamento não encontrado")), cpfCliente);
    }

    public Orcamento orcamentoDaOs(UUID osId, String cpfCliente) {
        return verificarDono(orcamentoRepository.buscarPorOsId(osId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Orçamento da OS não encontrado")), cpfCliente);
    }

    public List<Pagamento> pagamentos(UUID orcamentoId, String cpfCliente) {
        orcamento(orcamentoId, cpfCliente);
        return pagamentoRepository.listarPorOrcamento(orcamentoId);
    }

    private static Orcamento verificarDono(Orcamento o, String cpfCliente) {
        if (cpfCliente != null && !o.pertenceAoCpf(cpfCliente)) {
            throw new AccessDeniedException("Orçamento pertence a outro cliente.");
        }
        return o;
    }
}
