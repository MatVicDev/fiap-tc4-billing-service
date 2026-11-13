package br.com.oficina.billing.domain.orcamento;

import java.util.Optional;
import java.util.UUID;

public interface OrcamentoRepository {
    Orcamento salvar(Orcamento orcamento);
    Optional<Orcamento> buscarPorId(UUID id);
    Optional<Orcamento> buscarPorOsId(UUID osId);
}
