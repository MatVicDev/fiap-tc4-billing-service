package br.com.oficina.billing.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static br.com.oficina.billing.infrastructure.messaging.TopologiaRabbit.*;

@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange exchangeComandos() {
        return ExchangeBuilder.topicExchange(EXCHANGE_COMANDOS).durable(true).build();
    }

    @Bean
    TopicExchange exchangeEventos() {
        return ExchangeBuilder.topicExchange(EXCHANGE_EVENTOS).durable(true).build();
    }

    @Bean
    TopicExchange exchangeDlx() {
        return ExchangeBuilder.topicExchange(EXCHANGE_DLX).durable(true).build();
    }

    @Bean
    Queue filaComandos() {
        return QueueBuilder.durable(FILA_COMANDOS)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(FILA_COMANDOS_DLQ)
                .build();
    }

    @Bean
    Queue filaComandosDlq() {
        return QueueBuilder.durable(FILA_COMANDOS_DLQ).build();
    }

    @Bean
    Binding comandosBilling(Queue filaComandos, TopicExchange exchangeComandos) {
        return BindingBuilder.bind(filaComandos).to(exchangeComandos).with("billing.#");
    }

    @Bean
    Binding dlqComandos(Queue filaComandosDlq, TopicExchange exchangeDlx) {
        return BindingBuilder.bind(filaComandosDlq).to(exchangeDlx).with(FILA_COMANDOS_DLQ);
    }
}
