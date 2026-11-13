package br.com.oficina.billing.domain.pagamento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagamentoRepository {
    Pagamento salvar(Pagamento pagamento);
    Optional<Pagamento> buscarPorId(UUID id);
    List<Pagamento> listarPorOrcamento(UUID orcamentoId);
}
