package br.com.oficina.billing.infrastructure.messaging;

import br.com.oficina.billing.application.ProcessarComandoUseCase;
import br.com.oficina.billing.domain.mensageria.Mensagem;
import br.com.oficina.billing.infrastructure.observability.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ComandosListener {
    private final ProcessarComandoUseCase useCase;
    private final JsonMapper jsonMapper;

    @RabbitListener(queues = TopologiaRabbit.FILA_COMANDOS)
    public void receber(Message message) {
        Mensagem comando = jsonMapper.readValue(message.getBody(), Mensagem.class);
        try {
            if (comando.correlationId() != null) {
                MDC.put(CorrelationIdFilter.MDC_KEY, comando.correlationId());
            }
            MDC.put("osId", String.valueOf(comando.osId()));
            useCase.processar(comando);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
            MDC.remove("osId");
        }
    }
}
