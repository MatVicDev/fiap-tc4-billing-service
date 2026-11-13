package br.com.oficina.billing.domain.exception;

public class DomainException extends RuntimeException {

    public DomainException(String mensagem) {
        super(mensagem);
    }
}
