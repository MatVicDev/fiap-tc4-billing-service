package br.com.oficina.billing.infrastructure.persistence;

import br.com.oficina.billing.domain.orcamento.Orcamento;
import br.com.oficina.billing.domain.orcamento.OrcamentoRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrcamentoRepositoryJpa extends JpaRepository<Orcamento, UUID>, OrcamentoRepository {

    @Override
    default Orcamento salvar(Orcamento orcamento) {
        return save(orcamento);
    }

    @Override
    default Optional<Orcamento> buscarPorId(UUID id) {
        return findById(id);
    }

    @Override
    default Optional<Orcamento> buscarPorOsId(UUID osId) {
        return findByOsId(osId);
    }

    Optional<Orcamento> findByOsId(UUID osId);
}
