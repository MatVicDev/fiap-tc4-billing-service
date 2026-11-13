package br.com.oficina.billing.infrastructure;

import br.com.oficina.billing.application.ProcessarComandoUseCase;
import br.com.oficina.billing.domain.mensageria.Mensagem;
import br.com.oficina.billing.infrastructure.messaging.ComandosListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Testes do listener de comandos do billing-service")
class ListenerBillingTest {

    @Test
    @DisplayName("Desserializa o comando e delega ao caso de uso")
    void listener() {
        JsonMapper json = JsonMapper.builder().build();
        ProcessarComandoUseCase useCase = mock(ProcessarComandoUseCase.class);
        Mensagem cmd = Mensagem.nova("billing.gerar-orcamento", UUID.randomUUID(), "corr", Map.of("valorTotal", "10.00"));

        new ComandosListener(useCase, json).receber(new Message(json.writeValueAsBytes(cmd), new MessageProperties()));

        verify(useCase).processar(argThat(m -> m.id().equals(cmd.id()) && "10.00".equals(m.texto("valorTotal"))));
    }
}
