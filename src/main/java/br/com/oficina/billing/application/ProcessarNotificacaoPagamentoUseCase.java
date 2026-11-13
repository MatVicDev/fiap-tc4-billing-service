package br.com.oficina.billing.application;

import br.com.oficina.billing.domain.mensageria.Mensagem;
import br.com.oficina.billing.domain.mensageria.PublicadorMensagens;
import br.com.oficina.billing.domain.orcamento.OrcamentoRepository;
import br.com.oficina.billing.domain.pagamento.GatewayPagamento;
import br.com.oficina.billing.domain.pagamento.Pagamento;
import br.com.oficina.billing.domain.pagamento.PagamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static br.com.oficina.billing.domain.mensageria.TiposMensagem.EVT_PAGAMENTO_CONFIRMADO;
import static br.com.oficina.billing.domain.mensageria.TiposMensagem.EVT_PAGAMENTO_RECUSADO;

/**
 * Processa a notificação de pagamento do Mercado Pago: a notificação traz só o id do
 * pagamento; o status real é sempre consultado na API do provedor antes de ser aceito.
 * Idempotente — o Mercado Pago reenvia notificações até receber HTTP 200.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessarNotificacaoPagamentoUseCase {
    private final GatewayPagamento gateway;
    private final PagamentoRepository pagamentoRepository;
    private final OrcamentoRepository orcamentoRepository;
    private final PublicadorMensagens publicador;

    @Transactional
    public Optional<Pagamento> processar(String gatewayPagamentoId) {
        GatewayPagamento.ConsultaPagamento consulta = gateway.consultar(gatewayPagamentoId);
        Optional<Pagamento> pagamentoOpt = referencia(consulta).flatMap(pagamentoRepository::buscarPorId);
        if (pagamentoOpt.isEmpty()) {
            log.warn("Notificação do pagamento {} sem external_reference conhecida ({}) — ignorada",
                    gatewayPagamentoId, consulta.referenciaExterna());
            return Optional.empty();
        }
        Pagamento pagamento = pagamentoOpt.get();
        if (pagamento.getStatus().isFinal() || consulta.situacao() == GatewayPagamento.Situacao.PENDENTE) {
            log.info("Pagamento {} em {} / provedor {} — nada a fazer", pagamento.getId(), pagamento.getStatus(), consulta.situacao());
            return pagamentoOpt;
        }

        Map<String, Object> dados = new HashMap<>();
        dados.put("pagamentoId", pagamento.getId().toString());
        dados.put("orcamentoId", pagamento.getOrcamentoId().toString());
        dados.put("gatewayPagamentoId", gatewayPagamentoId);

        if (consulta.situacao() == GatewayPagamento.Situacao.APROVADO) {
            pagamento.aprovar(gatewayPagamentoId, consulta.detalhe());
            orcamentoRepository.buscarPorId(pagamento.getOrcamentoId()).ifPresent(o -> {
                o.registrarPagamento();
                orcamentoRepository.salvar(o);
            });
            dados.put("valor", pagamento.getValor().toPlainString());
            publicar(EVT_PAGAMENTO_CONFIRMADO, pagamento, dados);
        } else {
            pagamento.recusar(gatewayPagamentoId, consulta.detalhe());
            dados.put("motivo", consulta.detalhe() == null ? "recusado" : consulta.detalhe());
            publicar(EVT_PAGAMENTO_RECUSADO, pagamento, dados);
        }
        return Optional.of(pagamentoRepository.salvar(pagamento));
    }

    private void publicar(String tipo, Pagamento pagamento, Map<String, Object> dados) {
        log.info("Pagamento {} da OS {}: {}", pagamento.getId(), pagamento.getOsId(), tipo);
        publicador.publicarEvento(Mensagem.nova(tipo, pagamento.getOsId(), MDC.get("correlationId"), dados));
    }

    private static Optional<UUID> referencia(GatewayPagamento.ConsultaPagamento consulta) {
        try {
            return Optional.ofNullable(consulta.referenciaExterna()).map(UUID::fromString);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
