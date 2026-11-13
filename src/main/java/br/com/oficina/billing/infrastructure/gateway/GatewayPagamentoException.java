package br.com.oficina.billing.infrastructure.gateway;

public class GatewayPagamentoException extends RuntimeException {
    public GatewayPagamentoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
