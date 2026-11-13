package br.com.oficina.billing.application;

import br.com.oficina.billing.domain.mensageria.Mensagem;
import br.com.oficina.billing.domain.mensageria.MensagensProcessadas;
import br.com.oficina.billing.domain.mensageria.PublicadorMensagens;
import br.com.oficina.billing.domain.orcamento.ItemOrcamento;
import br.com.oficina.billing.domain.orcamento.Orcamento;
import br.com.oficina.billing.domain.orcamento.OrcamentoRepository;
import br.com.oficina.billing.domain.pagamento.GatewayPagamento;
import br.com.oficina.billing.domain.pagamento.Pagamento;
import br.com.oficina.billing.domain.pagamento.PagamentoRepository;
import br.com.oficina.billing.domain.pagamento.StatusPagamento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static br.com.oficina.billing.domain.mensageria.TiposMensagem.*;

/**
 * Participante da Saga: gera orçamentos e executa as compensações financeiras
 * (cancelamento de orçamento e estorno no Mercado Pago) pedidas pelo orquestrador.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessarComandoUseCase {
    private final OrcamentoRepository orcamentoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final GatewayPagamento gateway;
    private final PublicadorMensagens publicador;
    private final MensagensProcessadas processadas;

    @Transactional
    public void processar(Mensagem comando) {
        if (!processadas.registrarSeNova(comando.id(), comando.tipo())) {
            log.info("Comando {} ({}) já processado — ignorando", comando.id(), comando.tipo());
            return;
        }
        log.info("Processando comando {} da OS {}", comando.tipo(), comando.osId());
        switch (comando.tipo()) {
            case CMD_GERAR_ORCAMENTO -> gerarOrcamento(comando);
            case CMD_CANCELAR_ORCAMENTO -> cancelarOrcamento(comando);
            case CMD_ESTORNAR_PAGAMENTO -> estornarPagamento(comando);
            default -> log.warn("Comando desconhecido ignorado: {}", comando.tipo());
        }
    }

    private void gerarOrcamento(Mensagem cmd) {
        Orcamento orcamento = orcamentoRepository.buscarPorOsId(cmd.osId()).orElseGet(() -> orcamentoRepository.salvar(new Orcamento(
                cmd.osId(), cmd.uuid("clienteId"), cmd.texto("nomeCliente"), cmd.texto("emailCliente"), cmd.texto("cpfCliente"),
                cmd.lista("itens").stream().map(ProcessarComandoUseCase::paraItem).toList())));
        publicar(EVT_ORCAMENTO_GERADO, orcamento.getOsId(), cmd.correlationId(), Map.of(
                "orcamentoId", orcamento.getId().toString(),
                "valorTotal", orcamento.getValorTotal().toPlainString()));
    }

    private void cancelarOrcamento(Mensagem cmd) {
        Optional<Orcamento> orcamento = buscarOrcamento(cmd);
        boolean cancelado = orcamento.map(o -> {
            boolean mudou = o.cancelar(cmd.texto("motivo"));
            orcamentoRepository.salvar(o);
            return mudou;
        }).orElse(false);
        publicar(EVT_ORCAMENTO_CANCELADO, cmd.osId(), cmd.correlationId(), Map.of(
                "orcamentoId", orcamento.map(o -> o.getId().toString()).orElse(""),
                "status", orcamento.map(o -> o.getStatus().name()).orElse("INEXISTENTE"),
                "alterado", cancelado));
    }

    private void estornarPagamento(Mensagem cmd) {
        Optional<Pagamento> pagamentoOpt = Optional.ofNullable(cmd.uuid("pagamentoId")).flatMap(pagamentoRepository::buscarPorId);
        Map<String, Object> dados = new HashMap<>();
        dados.put("pagamentoId", cmd.texto("pagamentoId"));

        if (pagamentoOpt.isPresent() && pagamentoOpt.get().getStatus() == StatusPagamento.APROVADO) {
            Pagamento pagamento = pagamentoOpt.get();
            // Chave de idempotência = id do pagamento: reprocessar o comando não estorna duas vezes.
            gateway.estornar(pagamento.getGatewayPagamentoId(), pagamento.getValor(), "estorno-" + pagamento.getId());
            pagamento.estornar();
            pagamentoRepository.salvar(pagamento);
            orcamentoRepository.buscarPorId(pagamento.getOrcamentoId()).ifPresent(o -> {
                o.registrarEstorno(cmd.texto("motivo"));
                orcamentoRepository.salvar(o);
            });
            dados.put("valorEstornado", pagamento.getValor().toPlainString());
            log.warn("Pagamento {} da OS {} estornado: {}", pagamento.getId(), cmd.osId(), cmd.texto("motivo"));
        } else {
            dados.put("observacao", pagamentoOpt.map(p -> "Pagamento em " + p.getStatus() + " — nada a estornar")
                    .orElse("Pagamento não encontrado — nada a estornar"));
        }
        publicar(EVT_PAGAMENTO_ESTORNADO, cmd.osId(), cmd.correlationId(), dados);
    }

    private Optional<Orcamento> buscarOrcamento(Mensagem cmd) {
        UUID id = cmd.uuid("orcamentoId");
        return id != null ? orcamentoRepository.buscarPorId(id) : orcamentoRepository.buscarPorOsId(cmd.osId());
    }

    private void publicar(String tipo, UUID osId, String correlationId, Map<String, Object> dados) {
        publicador.publicarEvento(Mensagem.nova(tipo, osId, correlationId, dados));
    }

    static ItemOrcamento paraItem(Map<String, Object> m) {
        Object ref = m.get("referenciaId");
        return new ItemOrcamento(
                String.valueOf(m.get("tipo")),
                ref == null ? null : UUID.fromString(String.valueOf(ref)),
                String.valueOf(m.get("descricao")),
                new BigDecimal(String.valueOf(m.get("precoUnitario"))),
                Integer.valueOf(String.valueOf(m.get("quantidade"))));
    }
}
