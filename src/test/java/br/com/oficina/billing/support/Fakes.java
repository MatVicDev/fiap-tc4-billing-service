package br.com.oficina.billing.support;

import br.com.oficina.billing.domain.mensageria.Mensagem;
import br.com.oficina.billing.domain.mensageria.MensagensProcessadas;
import br.com.oficina.billing.domain.mensageria.PublicadorMensagens;
import br.com.oficina.billing.domain.orcamento.Orcamento;
import br.com.oficina.billing.domain.orcamento.OrcamentoRepository;
import br.com.oficina.billing.domain.pagamento.Pagamento;
import br.com.oficina.billing.domain.pagamento.PagamentoRepository;

import java.util.*;

public final class Fakes {
    private Fakes() {
    }

    public static class Orcamentos implements OrcamentoRepository {
        public final Map<UUID, Orcamento> dados = new LinkedHashMap<>();

        public Orcamento salvar(Orcamento o) { dados.put(o.getId(), o); return o; }
        public Optional<Orcamento> buscarPorId(UUID id) { return Optional.ofNullable(dados.get(id)); }
        public Optional<Orcamento> buscarPorOsId(UUID osId) {
            return dados.values().stream().filter(o -> o.getOsId().equals(osId)).findFirst();
        }
    }

    public static class Pagamentos implements PagamentoRepository {
        public final Map<UUID, Pagamento> dados = new LinkedHashMap<>();

        public Pagamento salvar(Pagamento p) { dados.put(p.getId(), p); return p; }
        public Optional<Pagamento> buscarPorId(UUID id) { return Optional.ofNullable(dados.get(id)); }
        public List<Pagamento> listarPorOrcamento(UUID orcamentoId) {
            return dados.values().stream().filter(p -> p.getOrcamentoId().equals(orcamentoId)).toList();
        }
    }

    public static class Publicador implements PublicadorMensagens {
        public final List<Mensagem> eventos = new ArrayList<>();

        public void enviarComando(Mensagem comando) { throw new UnsupportedOperationException(); }
        public void publicarEvento(Mensagem evento) { eventos.add(evento); }

        public List<String> tipos() { return eventos.stream().map(Mensagem::tipo).toList(); }

        public Mensagem ultimo(String tipo) {
            return eventos.stream().filter(e -> e.tipo().equals(tipo)).reduce((a, b) -> b)
                    .orElseThrow(() -> new AssertionError("Evento " + tipo + " não publicado: " + tipos()));
        }
    }

    public static class Processadas implements MensagensProcessadas {
        private final Set<UUID> ids = new HashSet<>();

        public boolean registrarSeNova(UUID id, String tipo) { return ids.add(id); }
    }
}
