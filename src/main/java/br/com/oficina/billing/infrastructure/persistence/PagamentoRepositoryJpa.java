package br.com.oficina.billing.infrastructure.persistence;

import br.com.oficina.billing.domain.pagamento.Pagamento;
import br.com.oficina.billing.domain.pagamento.PagamentoRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PagamentoRepositoryJpa extends JpaRepository<Pagamento, UUID>, PagamentoRepository {

    @Override
    default Pagamento salvar(Pagamento pagamento) {
        return save(pagamento);
    }

    @Override
    default Optional<Pagamento> buscarPorId(UUID id) {
        return findById(id);
    }

    @Override
    default List<Pagamento> listarPorOrcamento(UUID orcamentoId) {
        return findByOrcamentoIdOrderByCriadoEmDesc(orcamentoId);
    }

    List<Pagamento> findByOrcamentoIdOrderByCriadoEmDesc(UUID orcamentoId);
}
