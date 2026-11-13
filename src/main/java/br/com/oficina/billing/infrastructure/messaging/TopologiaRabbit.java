package br.com.oficina.billing.infrastructure.messaging;

public final class TopologiaRabbit {

    private TopologiaRabbit() {
    }

    public static final String EXCHANGE_COMANDOS = "oficina.saga.comandos";
    public static final String EXCHANGE_EVENTOS = "oficina.saga.eventos";
    public static final String EXCHANGE_DLX = "oficina.saga.dlx";

    /** Fila de comandos deste participante: tudo com routing key billing.* */
    public static final String FILA_COMANDOS = "billing-service.comandos";
    public static final String FILA_COMANDOS_DLQ = FILA_COMANDOS + ".dlq";

    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
}
